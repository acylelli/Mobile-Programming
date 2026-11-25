import express, { type NextFunction, type Request, type Response } from "express";
import type { PlaceSearchResponseDto } from "./contract.ts";
import { UpstreamError } from "./contract.ts";
import { type Clock, clientIp, DailyQuota, RateLimiter, TtlCache } from "./guard.ts";
import { fetchKakaoPlaces } from "./kakao.ts";
import { fetchOdsayRaw, type OdsayResponse, toRouteResponse } from "./odsay.ts";

/** 남용 방지 한도. 환경변수로 바꿀 수 있다(src/index.ts). */
export interface GuardLimits {
  /** IP 하나가 1분에 보낼 수 있는 경로 조회 수 */
  routesPerMinute: number;
  /** IP 하나가 1분에 보낼 수 있는 장소 검색 수 (앱은 입력 0.3초 뒤에 검색한다) */
  searchPerMinute: number;
  /** 하루(한국 시간) ODsay 호출 상한 */
  odsayDailyLimit: number;
  /** 하루(한국 시간) Kakao 호출 상한 */
  kakaoDailyLimit: number;
}

export const DEFAULT_LIMITS: GuardLimits = {
  routesPerMinute: 20,
  searchPerMinute: 60,
  odsayDailyLimit: 1_000,
  kakaoDailyLimit: 10_000,
};

export interface AppConfig {
  odsayApiKey: string;
  kakaoRestApiKey: string;
  limits?: Partial<GuardLimits>;
  /** 테스트에서 외부 호출을 대신한다. */
  fetchImpl?: typeof fetch;
  /** 테스트에서 시간을 대신한다. */
  clock?: Clock;
}

const MAX_QUERY_LENGTH = 100;
const MINUTE_MS = 60_000;
/** 같은 출발·도착(약 10m 단위) 경로는 1분 동안 다시 묻지 않는다. 알람 계산에는 충분히 신선하다. */
const ROUTE_CACHE_TTL_MS = 60_000;
/** 장소 검색 결과는 자주 바뀌지 않으므로 10분 동안 재사용한다. */
const SEARCH_CACHE_TTL_MS = 10 * 60_000;

export function createApp(config: AppConfig) {
  const limits = { ...DEFAULT_LIMITS, ...config.limits };
  const clock = config.clock ?? Date.now;
  const routeLimiter = new RateLimiter(limits.routesPerMinute, MINUTE_MS, clock);
  const searchLimiter = new RateLimiter(limits.searchPerMinute, MINUTE_MS, clock);
  const odsayQuota = new DailyQuota(limits.odsayDailyLimit, clock);
  const kakaoQuota = new DailyQuota(limits.kakaoDailyLimit, clock);
  const routeCache = new TtlCache<OdsayResponse>(ROUTE_CACHE_TTL_MS, 1_000, clock);
  const searchCache = new TtlCache<PlaceSearchResponseDto>(SEARCH_CACHE_TTL_MS, 1_000, clock);

  const app = express();
  app.disable("x-powered-by");

  // 헬스체크(cron-job.org, Render)는 제한하지 않는다.
  app.get("/healthz", (_req, res) => {
    res.json({ ok: true });
  });

  app.get("/v1/transit/routes", async (req, res) => {
    if (rejectIfLimited(req, res, routeLimiter)) return;

    const originLat = latitude(req.query.origin_lat);
    const originLng = longitude(req.query.origin_lng);
    const destinationLat = latitude(req.query.destination_lat);
    const destinationLng = longitude(req.query.destination_lng);
    const departAt = typeof req.query.depart_at === "string" ? new Date(req.query.depart_at) : null;

    if (originLat === null || originLng === null || destinationLat === null || destinationLng === null) {
      res.status(400).json({ error: "origin_lat, origin_lng, destination_lat, destination_lng 가 올바른 좌표여야 합니다." });
      return;
    }
    if (!departAt || Number.isNaN(departAt.getTime())) {
      res.status(400).json({ error: "depart_at 은 ISO-8601 시각이어야 합니다." });
      return;
    }

    const coords = { originLat, originLng, destinationLat, destinationLng };
    const key = [originLat, originLng, destinationLat, destinationLng].map((v) => v.toFixed(4)).join(",");
    const cached = routeCache.get(key);
    if (cached) {
      res.json(toRouteResponse(cached, departAt));
      return;
    }
    if (rejectIfQuotaExceeded(res, odsayQuota, "ODsay")) return;
    const raw = await fetchOdsayRaw(config.odsayApiKey, coords, config.fetchImpl);
    // 오류 응답이면 여기서 예외가 나므로 캐시에는 정상 응답만 들어간다.
    const body = toRouteResponse(raw, departAt);
    routeCache.set(key, raw);
    res.json(body);
  });

  app.get("/v1/places/search", async (req, res) => {
    if (rejectIfLimited(req, res, searchLimiter)) return;

    const query = typeof req.query.query === "string" ? req.query.query.trim() : "";
    if (!query || query.length > MAX_QUERY_LENGTH) {
      res.status(400).json({ error: `query 는 1~${MAX_QUERY_LENGTH}자여야 합니다.` });
      return;
    }
    const key = query.toLowerCase().replace(/\s+/g, " ");
    const cached = searchCache.get(key);
    if (cached) {
      res.json(cached);
      return;
    }
    if (rejectIfQuotaExceeded(res, kakaoQuota, "Kakao")) return;
    const body = await fetchKakaoPlaces(config.kakaoRestApiKey, query, config.fetchImpl);
    searchCache.set(key, body);
    res.json(body);
  });

  app.use((_req, res) => {
    res.status(404).json({ error: "not found" });
  });

  // 외부 API 오류 메시지에는 키가 들어가지 않지만, 앱에는 상태코드만 넘기고 자세한 내용은 서버 로그에만 남긴다.
  app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
    const status = err instanceof UpstreamError ? err.status : 500;
    console.error(`[proxy] ${status}`, err instanceof Error ? err.message : err);
    res.status(status).json({ error: status === 500 ? "internal error" : "upstream error" });
  });

  return app;
}

/** 앱은 429 를 서버 오류로 받아 캐시된 경로로 대체한다. */
function rejectIfLimited(req: Request, res: Response, limiter: RateLimiter): boolean {
  const retryAfter = limiter.take(clientIp(req.headers, req.socket.remoteAddress));
  if (retryAfter === null) return false;
  res.set("Retry-After", String(retryAfter)).status(429).json({ error: "too many requests" });
  return true;
}

function rejectIfQuotaExceeded(res: Response, quota: DailyQuota, name: string): boolean {
  if (quota.take()) return false;
  console.error(`[proxy] 429 ${name} 하루 호출 상한(${quota.limit}) 도달`);
  res.set("Retry-After", String(quota.secondsUntilReset())).status(429).json({ error: "daily limit reached" });
  return true;
}

function latitude(value: unknown): number | null {
  return coordinate(value, 90);
}

function longitude(value: unknown): number | null {
  return coordinate(value, 180);
}

function coordinate(value: unknown, limit: number): number | null {
  if (typeof value !== "string" || value.trim() === "") return null;
  const number = Number(value);
  return Number.isFinite(number) && Math.abs(number) <= limit ? number : null;
}
