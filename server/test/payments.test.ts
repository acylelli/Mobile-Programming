import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { test } from "node:test";
import { createApp } from "../src/app.ts";

const now = Date.parse("2026-11-25T10:00:00Z");

async function withServer(
  tossKeys: { clientKey?: string; secretKey?: string },
  confirm: (body: Record<string, unknown>, auth: string | null) => Response,
  run: (base: string, confirms: Array<Record<string, unknown>>) => Promise<void>,
) {
  const confirms: Array<Record<string, unknown>> = [];
  const fakeFetch: typeof fetch = async (input, init) => {
    if (String(input) === "https://api.tosspayments.com/v1/payments/confirm") {
      const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
      confirms.push(body);
      return confirm(body, new Headers(init?.headers).get("authorization"));
    }
    throw new Error(`unexpected ${String(input)}`);
  };
  const server = createApp({ odsayApiKey: "k", kakaoRestApiKey: "k", tossKeys, fetchImpl: fakeFetch, clock: () => now }).listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  try {
    await run(`http://127.0.0.1:${(server.address() as AddressInfo).port}`, confirms);
  } finally {
    server.close();
  }
}

const keys = { clientKey: "test_ck_client", secretKey: "test_sk_secret" };
const approve = () => Response.json({ status: "DONE" });

async function checkout(base: string, product: string): Promise<{ orderId: string; html: string }> {
  const res = await fetch(`${base}/pay/checkout?product=${product}`);
  assert.equal(res.status, 200);
  const html = await res.text();
  const orderId = html.match(/orderId: "(ra_[0-9a-f-]+)"/)![1]!;
  return { orderId, html };
}

test("결제 페이지는 서버가 정한 금액과 클라이언트 키로 결제창을 띄운다", async () => {
  await withServer(keys, approve, async (base) => {
    const { html } = await checkout(base, "routealarm_pro_yearly");
    assert.match(html, /TossPayments\("test_ck_client"\)/);
    assert.match(html, /value: 24000/);
    assert.match(html, /successUrl: "http:\/\/127\.0\.0\.1:\d+\/pay\/success"/);
    assert.doesNotMatch(html, /test_sk_secret/);
    assert.equal((await fetch(`${base}/pay/checkout?product=unknown`)).status, 400);
  });
});

test("승인되면 이용 기간이 기록되고 앱이 결과를 조회할 수 있다", async () => {
  await withServer(
    keys,
    (body, auth) => {
      // 시크릿 키 + ":" 를 Base64 로 인코딩한 Basic 인증
      assert.equal(auth, `Basic ${Buffer.from("test_sk_secret:").toString("base64")}`);
      assert.equal(body.amount, 2900);
      return Response.json({ status: "DONE" });
    },
    async (base, confirms) => {
      const { orderId } = await checkout(base, "routealarm_pro_monthly");
      const before = (await (await fetch(`${base}/v1/payments/${orderId}`)).json()) as { status: string };
      assert.equal(before.status, "READY");

      const success = await fetch(`${base}/pay/success?paymentKey=pk_1&orderId=${orderId}&amount=2900`);
      assert.equal(success.status, 200);
      assert.match(await success.text(), /intent:\/\/payment\?orderId=ra_.+result=success#Intent;scheme=routealarm;package=com\.example\.routealarm;end/);

      const after = (await (await fetch(`${base}/v1/payments/${orderId}`)).json()) as Record<string, any>;
      assert.equal(after.status, "PAID");
      assert.deepEqual(after.subscription, {
        product_id: "routealarm_pro_monthly",
        started_at: "2026-11-25T10:00:00.000Z",
        expires_at: "2026-12-25T10:00:00.000Z",
        auto_renew: false,
      });

      // 새로고침해도 다시 승인하지 않는다
      await fetch(`${base}/pay/success?paymentKey=pk_1&orderId=${orderId}&amount=2900`);
      assert.equal(confirms.length, 1);
    },
  );
});

test("결제창에서 금액이 바뀌면 승인하지 않는다", async () => {
  await withServer(keys, approve, async (base, confirms) => {
    const { orderId } = await checkout(base, "routealarm_pro_yearly");
    const res = await fetch(`${base}/pay/success?paymentKey=pk_1&orderId=${orderId}&amount=100`);
    assert.equal(res.status, 400);
    assert.equal(confirms.length, 0);
    const order = (await (await fetch(`${base}/v1/payments/${orderId}`)).json()) as Record<string, unknown>;
    assert.equal(order.status, "FAILED");
    assert.equal(order.subscription, null);
  });
});

test("토스가 승인을 거절하거나 사용자가 취소하면 실패로 남는다", async () => {
  await withServer(keys, () => Response.json({ code: "REJECT_CARD_COMPANY", message: "카드사에서 거절했어요" }, { status: 400 }), async (base) => {
    const { orderId } = await checkout(base, "routealarm_pro_yearly");
    const res = await fetch(`${base}/pay/success?paymentKey=pk_1&orderId=${orderId}&amount=24000`);
    assert.equal(res.status, 502);
    const order = (await (await fetch(`${base}/v1/payments/${orderId}`)).json()) as Record<string, unknown>;
    assert.equal(order.status, "FAILED");
    assert.equal(order.fail_code, "REJECT_CARD_COMPANY");

    const canceled = await checkout(base, "routealarm_pro_monthly");
    const fail = await fetch(`${base}/pay/fail?code=PAY_PROCESS_CANCELED&message=x&orderId=${canceled.orderId}`);
    assert.match(await fail.text(), /결제를 취소했어요/);
  });
});

test("키가 없으면 결제 경로를 끈다", async () => {
  await withServer({}, approve, async (base) => {
    assert.equal((await fetch(`${base}/pay/checkout?product=routealarm_pro_yearly`)).status, 503);
    assert.equal((await fetch(`${base}/v1/payments/ra_x`)).status, 503);
  });
});

test("앱이 먼저 주문을 만들면 주문 번호와 결제창 주소를 주고, 그 주소로 같은 주문의 결제창이 열린다", async () => {
  await withServer(keys, approve, async (base) => {
    const res = await fetch(`${base}/v1/payments/orders`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ product_id: "routealarm_pro_yearly" }),
    });
    assert.equal(res.status, 200);
    const { order_id, checkout_url } = (await res.json()) as { order_id: string; checkout_url: string };
    assert.match(order_id, /^ra_/);
    assert.equal(new URL(checkout_url).searchParams.get("orderId"), order_id);

    const page = await (await fetch(checkout_url)).text();
    assert.match(page, new RegExp(`orderId: "${order_id}"`));
    assert.match(page, /value: 24000/);

    // 결제 전 조회는 READY, 결제 후에는 같은 주문 번호로 결과를 확인할 수 있다
    assert.equal(((await (await fetch(`${base}/v1/payments/${order_id}`)).json()) as { status: string }).status, "READY");
    await fetch(`${base}/pay/success?paymentKey=pk&orderId=${order_id}&amount=24000`);
    assert.equal(((await (await fetch(`${base}/v1/payments/${order_id}`)).json()) as { status: string }).status, "PAID");

    // 이미 결제한 주문의 결제창은 다시 열리지 않는다
    assert.match(await (await fetch(checkout_url)).text(), /이미 결제한 주문이에요/);

    const bad = await fetch(`${base}/v1/payments/orders`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
    assert.equal(bad.status, 400);
  });
});
