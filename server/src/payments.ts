import { randomUUID } from "node:crypto";
import express, { type Request, type Response } from "express";
import { type Clock, clientIp, RateLimiter, TtlCache } from "./guard.ts";

/*
 * PRO 기간제 이용권 테스트 결제(토스페이먼츠, API 개별 연동 키).
 *
 * 흐름
 *  1. 앱이 POST /v1/payments/orders 로 주문을 만들고(주문 번호를 기억해 둠) 받은 checkout_url 을 브라우저로 연다.
 *     → 서버가 토스 결제창을 띄우는 페이지를 준다. (예전 앱은 GET /pay/checkout?product=… 로 바로 열며, 이것도 계속 지원한다.)
 *  2. 결제창에서 결제하면 토스가 GET /pay/success?paymentKey&orderId&amount 로 돌려보낸다.
 *  3. 서버가 주문 금액과 비교한 뒤 시크릿 키로 결제를 승인(POST /v1/payments/confirm)하고 이용 기간을 기록한다.
 *  4. 결과 페이지가 앱 딥링크(routealarm://payment?orderId=…)로 돌아간다.
 *  5. 앱은 GET /v1/payments/{orderId} 로 서버가 승인한 결과를 받아 PRO 를 적용한다(앱은 결제 결과를 스스로 판단하지 않는다).
 *     딥링크로 돌아오지 않고 브라우저를 닫아도, 앱이 다시 열릴 때 기억해 둔 주문 번호로 같은 조회를 한다.
 *
 * 테스트 키(test_ck_/test_sk_)를 쓰는 동안은 실제 돈이 나가지 않는다.
 * 주문은 메모리에만 두므로 서버가 재시작하면 진행 중인 주문은 사라진다(테스트 범위).
 */

export interface PaymentProduct {
  amount: number;
  orderName: string;
  days: number;
}

/** 앱의 SubscriptionProduct 와 금액·ID 가 같아야 한다. 금액은 서버 값만 믿는다. */
export const PRODUCTS: Record<string, PaymentProduct> = {
  routealarm_pro_monthly: { amount: 2_900, orderName: "RouteAlarm PRO 1개월", days: 30 },
  routealarm_pro_yearly: { amount: 24_000, orderName: "RouteAlarm PRO 1년", days: 365 },
};

export interface TossKeys {
  clientKey?: string;
  secretKey?: string;
}

type OrderStatus = "READY" | "PAID" | "FAILED";

interface Order {
  orderId: string;
  productId: string;
  amount: number;
  status: OrderStatus;
  startedAt?: string;
  expiresAt?: string;
  failCode?: string;
}

const TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
const ORDER_TTL_MS = 24 * 60 * 60_000;
const APP_PACKAGE = "com.example.routealarm";

export function createPaymentsRouter(keys: TossKeys, clock: Clock, fetchImpl: typeof fetch = fetch) {
  const router = express.Router();
  const orders = new TtlCache<Order>(ORDER_TTL_MS, 5_000, clock);
  const limiter = new RateLimiter(30, 60_000, clock);
  const enabled = Boolean(keys.clientKey && keys.secretKey);

  router.use(["/pay", "/v1/payments"], (req, res, next) => {
    if (!enabled) {
      res.status(503).json({ error: "payments not configured" });
      return;
    }
    const retryAfter = limiter.take(clientIp(req.headers, req.socket.remoteAddress));
    if (retryAfter !== null) {
      res.set("Retry-After", String(retryAfter)).status(429).json({ error: "too many requests" });
      return;
    }
    next();
  });

  function createOrder(productId: string): Order | null {
    const product = PRODUCTS[productId];
    if (!product) return null;
    const order: Order = { orderId: `ra_${randomUUID()}`, productId, amount: product.amount, status: "READY" };
    orders.set(order.orderId, order);
    return order;
  }

  /** 앱이 결제 전에 주문을 만든다. 주문 번호를 앱이 먼저 알아야, 결제 후 앱으로 돌아오지 않아도 결과를 확인할 수 있다. */
  router.post("/v1/payments/orders", express.json({ limit: "1kb" }), (req, res) => {
    const order = createOrder(String((req.body as { product_id?: unknown } | undefined)?.product_id ?? ""));
    if (!order) {
      res.status(400).json({ error: "unknown product" });
      return;
    }
    const url = new URL(`${publicBaseUrl(req)}/pay/checkout`);
    url.searchParams.set("orderId", order.orderId);
    res.json({ order_id: order.orderId, checkout_url: url.toString() });
  });

  router.get("/pay/checkout", (req, res) => {
    // 새 앱: 미리 만든 주문(orderId) / 예전 앱: 상품(product)으로 여기서 주문을 만든다.
    const existing = typeof req.query.orderId === "string" ? orders.get(req.query.orderId) : undefined;
    const order = existing ?? (typeof req.query.product === "string" ? createOrder(req.query.product) : null);
    if (!order) {
      res.status(400).type("html").send(resultPage("주문을 찾을 수 없어요", "앱에서 다시 시도해 주세요.", null));
      return;
    }
    if (order.status !== "READY") {
      res.type("html").send(
        order.status === "PAID"
          ? resultPage("이미 결제한 주문이에요", "앱으로 돌아가면 PRO 가 적용돼요.", appLink(order, "success"))
          : resultPage("결제하지 못한 주문이에요", "앱에서 다시 결제해 주세요.", appLink(order, "fail")),
      );
      return;
    }
    const product = PRODUCTS[order.productId]!;
    const base = publicBaseUrl(req);
    res.type("html").send(
      checkoutPage({
        clientKey: keys.clientKey!,
        orderId: order.orderId,
        orderName: product.orderName,
        amount: product.amount,
        successUrl: `${base}/pay/success`,
        failUrl: `${base}/pay/fail`,
      }),
    );
  });

  router.get("/pay/success", async (req, res) => {
    const paymentKey = String(req.query.paymentKey ?? "");
    const orderId = String(req.query.orderId ?? "");
    const amount = Number(req.query.amount);
    const order = orders.get(orderId);
    if (!order || !paymentKey) {
      res.status(400).type("html").send(resultPage("주문을 찾을 수 없어요", "앱에서 다시 결제해 주세요.", null));
      return;
    }
    // 이미 승인된 주문(새로고침 등)은 다시 승인하지 않는다.
    if (order.status === "PAID") {
      res.type("html").send(resultPage("결제가 완료됐어요", "앱으로 돌아가면 PRO 가 적용돼요.", appLink(order, "success")));
      return;
    }
    // 결제창에서 금액이 바뀌었으면 승인하지 않는다.
    if (amount !== order.amount) {
      order.status = "FAILED";
      order.failCode = "AMOUNT_MISMATCH";
      res.status(400).type("html").send(resultPage("결제 금액이 맞지 않아요", "앱에서 다시 결제해 주세요.", appLink(order, "fail")));
      return;
    }
    const confirmed = await confirmPayment(keys.secretKey!, paymentKey, orderId, amount, fetchImpl);
    if (!confirmed.ok) {
      order.status = "FAILED";
      order.failCode = confirmed.code;
      console.error(`[payments] 승인 실패 ${confirmed.code}`);
      res.status(502).type("html").send(resultPage("결제를 승인하지 못했어요", confirmed.message, appLink(order, "fail")));
      return;
    }
    const started = new Date(clock());
    order.status = "PAID";
    order.startedAt = started.toISOString();
    order.expiresAt = new Date(started.getTime() + PRODUCTS[order.productId]!.days * 86_400_000).toISOString();
    res.type("html").send(resultPage("결제가 완료됐어요", "앱으로 돌아가면 PRO 가 적용돼요.", appLink(order, "success")));
  });

  router.get("/pay/fail", (req, res) => {
    const orderId = String(req.query.orderId ?? "");
    const code = String(req.query.code ?? "");
    const order = orders.get(orderId);
    if (order && order.status === "READY") {
      order.status = "FAILED";
      order.failCode = code;
    }
    // 사용자가 결제창을 닫은 경우(PAY_PROCESS_CANCELED)도 여기로 온다.
    const canceled = code === "PAY_PROCESS_CANCELED";
    res.type("html").send(
      resultPage(
        canceled ? "결제를 취소했어요" : "결제하지 못했어요",
        canceled ? "앱으로 돌아가 다시 시도할 수 있어요." : String(req.query.message ?? "잠시 후 다시 시도해 주세요."),
        order ? appLink(order, "fail") : null,
      ),
    );
  });

  /** 앱이 결제 결과를 확인한다. 승인된 주문이면 앱 계약(SubscriptionStatusDto) 형태로 이용 기간을 준다. */
  router.get("/v1/payments/:orderId", (req, res) => {
    const order = orders.get(req.params.orderId);
    if (!order) {
      res.status(404).json({ error: "order not found" });
      return;
    }
    res.json({
      order_id: order.orderId,
      status: order.status,
      fail_code: order.failCode ?? null,
      subscription:
        order.status === "PAID"
          ? { product_id: order.productId, started_at: order.startedAt, expires_at: order.expiresAt, auto_renew: false }
          : null,
    });
  });

  return router;
}

async function confirmPayment(
  secretKey: string,
  paymentKey: string,
  orderId: string,
  amount: number,
  fetchImpl: typeof fetch,
): Promise<{ ok: true } | { ok: false; code: string; message: string }> {
  try {
    const response = await fetchImpl(TOSS_CONFIRM_URL, {
      method: "POST",
      headers: {
        Authorization: `Basic ${Buffer.from(`${secretKey}:`).toString("base64")}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ paymentKey, orderId, amount }),
      signal: AbortSignal.timeout(10_000),
    });
    const body = (await response.json().catch(() => ({}))) as { status?: string; code?: string; message?: string };
    if (response.ok && body.status === "DONE") return { ok: true };
    return { ok: false, code: body.code ?? `HTTP_${response.status}`, message: body.message ?? "잠시 후 다시 시도해 주세요." };
  } catch (e) {
    return { ok: false, code: (e as Error).name, message: "결제 서버에 연결하지 못했어요." };
  }
}

/** Render 앞단 프록시를 거치므로 원래 요청의 https 주소를 다시 만든다. */
function publicBaseUrl(req: Request): string {
  const proto = String(req.headers["x-forwarded-proto"] ?? req.protocol).split(",")[0]!.trim();
  return `${proto}://${req.get("host")}`;
}

/** Android Chrome 은 intent:// 링크로 앱을 연다. 앱이 없으면 아무 일도 일어나지 않고 결과 페이지가 남는다. */
function appLink(order: Order, result: "success" | "fail"): string {
  const query = new URLSearchParams({ orderId: order.orderId, result });
  return `intent://payment?${query}#Intent;scheme=routealarm;package=${APP_PACKAGE};end`;
}

/** HTML 안 <script> 에 값을 넣을 때 태그가 끊기지 않게 한다. */
function jsValue(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

const PAGE_STYLE = `
  body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;background:#0E1220;color:#F2F4F8;
       display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}
  main{max-width:420px;width:100%;text-align:center}
  h1{font-size:22px;margin:0 0 8px} p{color:#A9B1C3;margin:0 0 24px;line-height:1.5}
  .badge{display:inline-block;font-size:12px;padding:4px 10px;border-radius:999px;background:#2A3350;color:#CFE86B;margin-bottom:16px}
  a.button,button{display:block;width:100%;padding:16px;border:0;border-radius:16px;background:#CFE86B;color:#0E1220;font-size:17px;font-weight:700;text-decoration:none}`;

function checkoutPage(p: { clientKey: string; orderId: string; orderName: string; amount: number; successUrl: string; failUrl: string }): string {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>RouteAlarm 결제</title><style>${PAGE_STYLE}</style>
<script src="https://js.tosspayments.com/v2/standard"></script></head>
<body><main>
  <span class="badge">테스트 결제 · 실제 청구 없음</span>
  <h1>${escapeHtml(p.orderName)}</h1>
  <p>${p.amount.toLocaleString("ko-KR")}원</p>
  <button id="pay">결제하기</button>
</main>
<script>
  const payment = TossPayments(${jsValue(p.clientKey)}).payment({ customerKey: TossPayments.ANONYMOUS });
  async function pay() {
    try {
      await payment.requestPayment({
        method: "CARD",
        amount: { currency: "KRW", value: ${jsValue(p.amount)} },
        orderId: ${jsValue(p.orderId)},
        orderName: ${jsValue(p.orderName)},
        successUrl: ${jsValue(p.successUrl)},
        failUrl: ${jsValue(p.failUrl)},
      });
    } catch (e) {
      // 결제창을 닫으면 여기로 온다. 버튼으로 다시 열 수 있다.
      console.log(e && e.code);
    }
  }
  document.getElementById("pay").addEventListener("click", pay);
  pay();
</script></body></html>`;
}

function resultPage(title: string, message: string, link: string | null): string {
  const button = link ? `<a class="button" href="${escapeHtml(link)}">앱으로 돌아가기</a>` : "";
  // 버튼을 누르지 않아도 앱으로 돌아가도록 한 번 시도한다(브라우저가 막으면 버튼으로 돌아간다).
  const auto = link ? `<script>setTimeout(function(){ location.href = ${jsValue(link)}; }, 300);</script>` : "";
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(title)}</title><style>${PAGE_STYLE}</style></head>
<body><main><span class="badge">테스트 결제</span><h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p>${button}</main>${auto}</body></html>`;
}
