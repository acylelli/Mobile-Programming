import express, { type NextFunction, type Request, type Response } from "express";
import type { PlaceSearchResponseDto } from "./contract.ts";
import { UpstreamError } from "./contract.ts";
import { type Clock, clientIp, DailyQuota, RateLimiter, TtlCache } from "./guard.ts";
import { fetchKakaoPlaces } from "./kakao.ts";
import { createGroupPassRouter, DEFAULT_GROUP_PASSES, type GroupPassConfig } from "./grouppass.ts";
import { createPaymentsRouter, type TossKeys } from "./payments.ts";
import { attachLaneGeometry, fetchOdsayRaw, firstRideTarget, type OdsayResponse, type RideTarget, toRouteResponse, toRoutesWithPaths } from "./odsay.ts";
import { type Arrival, applyArrival, fetchBusArrivals, fetchSubwayArrivals, firstCatchable, type RealtimeKeys } from "./realtime.ts";

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
  /** 하루(한국 시간) 실시간 도착 API 호출 상한 (지하철·서울 버스·경기 버스 각각) */
  realtimeDailyLimit: number;
}

export const DEFAULT_LIMITS: GuardLimits = {
  routesPerMinute: 20,
  searchPerMinute: 60,
  odsayDailyLimit: 1_000,
  kakaoDailyLimit: 10_000,
  realtimeDailyLimit: 900,
};

export interface AppConfig {
  odsayApiKey: string;
  kakaoRestApiKey: string;
  limits?: Partial<GuardLimits>;
  /** 없으면 실시간 도착 정보 없이 시간표 기준으로만 응답한다. */
  realtimeKeys?: RealtimeKeys;
  /** 없으면 결제 경로가 503 을 준다. */
  tossKeys?: TossKeys;
  /** 단체 이용권 코드. 없으면 테스트 코드를 쓴다. */
  groupPasses?: GroupPassConfig[];
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
/**
 * 실시간 도착 정보는 출발이 이만큼 가까울 때만 쓴다. 그보다 먼 출발에 "지금 3분 후 도착"을 붙이면
 * 사용자가 실제로 탈 차가 아니라서 오히려 틀린 안내가 된다.
 */
const REALTIME_WINDOW_MS = 30 * 60_000;
/** 실시간 조회는 짧게만 재사용한다. */
const REALTIME_CACHE_TTL_MS = 30_000;
/** 경로마다 첫 탑승 구간 하나씩, 상위 몇 개 경로까지만 조회한다(외부 호출 수 제한). */
const REALTIME_MAX_ROUTES = 3;

export function createApp(config: AppConfig) {
  const limits = { ...DEFAULT_LIMITS, ...config.limits };
  const clock = config.clock ?? Date.now;
  const routeLimiter = new RateLimiter(limits.routesPerMinute, MINUTE_MS, clock);
  const searchLimiter = new RateLimiter(limits.searchPerMinute, MINUTE_MS, clock);
  const odsayQuota = new DailyQuota(limits.odsayDailyLimit, clock);
  const kakaoQuota = new DailyQuota(limits.kakaoDailyLimit, clock);
  const routeCache = new TtlCache<OdsayResponse>(ROUTE_CACHE_TTL_MS, 1_000, clock);
  const searchCache = new TtlCache<PlaceSearchResponseDto>(SEARCH_CACHE_TTL_MS, 1_000, clock);
  const realtime = createRealtime(config.realtimeKeys ?? {}, limits.realtimeDailyLimit, clock, config.fetchImpl);

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
    let raw = routeCache.get(key);
    if (!raw) {
      if (rejectIfQuotaExceeded(res, odsayQuota, "ODsay")) return;
      raw = await fetchOdsayRaw(config.odsayApiKey, coords, config.fetchImpl);
      // 지도용 실제 노선 모양. 같은 하루 상한을 나눠 쓰고, 경로와 함께 캐시되므로 같은 경로는 다시 부르지 않는다.
      if (raw.error === undefined) {
        await attachLaneGeometry(config.odsayApiKey, raw, () => odsayQuota.take(), config.fetchImpl);
      }
      // 오류 응답이면 여기서 예외가 나므로 캐시에는 정상 응답만 들어간다.
      const body = toRouteResponse(raw, departAt);
      routeCache.set(key, raw);
      if (!realtime.applies(departAt)) {
        res.json(body);
        return;
      }
    } else if (!realtime.applies(departAt)) {
      res.json(toRouteResponse(raw, departAt));
      return;
    }
    res.json(await realtime.adjust(raw, departAt));
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

  app.use(createPaymentsRouter(config.tossKeys ?? {}, clock, config.fetchImpl));
  app.use(createGroupPassRouter(config.groupPasses ?? DEFAULT_GROUP_PASSES, clock));

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

/** 출발 30분 이내 경로의 첫 탑승 구간에 실시간 도착 정보를 반영한다. */
function createRealtime(keys: RealtimeKeys, dailyLimit: number, clock: Clock, fetchImpl?: typeof fetch) {
  // 조회 시각을 함께 저장해 두고, 캐시에서 꺼낼 때 그동안 지난 시간만큼 남은 시간을 줄인다.
  const cache = new TtlCache<{ list: Arrival[] | null; at: number }>(REALTIME_CACHE_TTL_MS, 500, clock);
  const quotas = {
    subway: new DailyQuota(dailyLimit, clock),
    seoul: new DailyQuota(dailyLimit, clock),
    gyeonggi: new DailyQuota(dailyLimit, clock),
  };
  const enabled = Boolean(keys.seoulSubwayKey || keys.dataGoKrKey);

  async function arrivals(target: RideTarget): Promise<Arrival[] | null> {
    const cacheKey = JSON.stringify({ kind: target.kind, query: target.query });
    const hit = cache.get(cacheKey);
    if (hit !== undefined) {
      const elapsed = (clock() - hit.at) / 1000;
      return hit.list?.map((a) => ({ ...a, seconds: a.seconds - elapsed })) ?? null;
    }
    let result: Arrival[] | null = null;
    if (target.kind === "subway") {
      if (!keys.seoulSubwayKey || !quotas.subway.take()) return null;
      result = await fetchSubwayArrivals(keys.seoulSubwayKey, target.query, fetchImpl, clock());
    } else {
      if (!keys.dataGoKrKey || !quotas[target.query.region].take()) return null;
      result = await fetchBusArrivals(keys.dataGoKrKey, target.query, fetchImpl);
    }
    cache.set(cacheKey, { list: result, at: clock() });
    return result;
  }

  return {
    applies(departAt: Date): boolean {
      const untilDeparture = departAt.getTime() - clock();
      // 1분 정도 지난 출발 시각까지는 "지금 출발"로 본다.
      return enabled && untilDeparture >= -60_000 && untilDeparture <= REALTIME_WINDOW_MS;
    },

    async adjust(raw: OdsayResponse, departAt: Date) {
      const pairs = toRoutesWithPaths(raw, departAt);
      const secondsUntilDeparture = Math.max(0, (departAt.getTime() - clock()) / 1000);
      const routes = await Promise.all(
        pairs.map(async ({ route, path }, index) => {
          if (index >= REALTIME_MAX_ROUTES) return route;
          const target = firstRideTarget(path);
          if (!target) return route;
          const list = await arrivals(target);
          if (!list) return route;
          // 첫 도보까지 마친 뒤 정류장에 닿는 시각
          const walkBefore = route.segments[0]?.mode === "WALK" ? route.segments[0].minutes : 0;
          const atStop = secondsUntilDeparture + walkBefore * 60;
          const catchable = firstCatchable(list, atStop, target.headwayMinutes);
          return catchable ? applyArrival(route, atStop, catchable) : route;
        }),
      );
      routes.sort((a, b) => a.total_minutes - b.total_minutes);
      return { realtime: true, routes };
    },
  };
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
