import express, { type NextFunction, type Request, type Response } from "express";
import type { PlaceSearchResponseDto } from "./contract.ts";
import { UpstreamError } from "./contract.ts";
import { type Clock, clientIp, DailyQuota, KeyedDailyQuota, RateLimiter, TtlCache } from "./guard.ts";
import { fetchKakaoPlaces } from "./kakao.ts";
import { fetchTaxiEstimate, type TaxiEstimateDto } from "./taxi.ts";
import { fetchForecast, type Forecast, latestBase, toGrid, weatherAt } from "./weather.ts";
import { createGroupPassRouter, DEFAULT_GROUP_PASSES, type GroupPassConfig } from "./grouppass.ts";
import { createPaymentsRouter, type TossKeys } from "./payments.ts";
import { attachLaneGeometry, fetchOdsayRaw, firstRideTarget, type OdsayResponse, type RideTarget, toRouteResponse, toRoutesWithPaths } from "./odsay.ts";
import {
  type Arrival,
  applyArrival,
  fetchBusArrivals,
  fetchSubwayArrivals,
  fetchTagoArrivals,
  findTagoNode,
  firstCatchable,
  type RealtimeKeys,
  type TagoBusQuery,
  type TagoNode,
} from "./realtime.ts";

/** 남용 방지 한도. 환경변수로 바꿀 수 있다(src/index.ts). */
export interface GuardLimits {
  /** IP 하나가 1분에 보낼 수 있는 경로 조회 수 */
  routesPerMinute: number;
  /** IP 하나가 1분에 보낼 수 있는 장소 검색 수 (앱은 입력 0.3초 뒤에 검색한다) */
  searchPerMinute: number;
  /** 하루(한국 시간) ODsay 호출 상한 */
  odsayDailyLimit: number;
  /** IP 하나가 하루에 일으킬 수 있는 ODsay 경로 검색 수(캐시 적중은 세지 않음) */
  odsayPerIpDailyLimit: number;
  /** 하루(한국 시간) Kakao 호출 상한 */
  kakaoDailyLimit: number;
  /** 하루(한국 시간) 실시간 도착 API 호출 상한 (지하철·서울 버스·경기 버스 각각) */
  realtimeDailyLimit: number;
  /** 하루(한국 시간) 기상청 단기예보 호출 상한 */
  weatherDailyLimit: number;
}

export const DEFAULT_LIMITS: GuardLimits = {
  routesPerMinute: 20,
  searchPerMinute: 60,
  odsayDailyLimit: 1_000,
  odsayPerIpDailyLimit: 150,
  kakaoDailyLimit: 10_000,
  realtimeDailyLimit: 900,
  weatherDailyLimit: 9_000,
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
/**
 * 같은 출발·도착(약 10m 단위) 경로는 20분 동안 다시 묻지 않는다.
 * ODsay 경로 검색은 출발 시각을 받지 않아 결과가 시각과 상관없고, 시각·실시간 도착은 요청마다 새로 계산한다.
 */
const ROUTE_CACHE_TTL_MS = 20 * 60_000;
/**
 * 예비 경로 캐시. 하루 상한이 가까울 때(절약 모드), 다 찼을 때, ODsay 가 오류(자체 한도 초과 등)일 때
 * 이 값으로 답해 앱이 경로를 아예 못 받는 일을 막는다. 이때 realtime=false 로 알려 앱이 여유를 더 둔다.
 */
const STALE_ROUTE_TTL_MS = 12 * 60 * 60_000;
/** 하루 상한의 이 비율을 넘으면 새 경로만 ODsay 에 묻고, 노선 모양(loadLane) 호출은 멈춘다. */
const SAVER_MODE_RATIO = 0.8;
const GEOMETRY_CACHE_TTL_MS = 24 * 60 * 60_000;
/** 장소 검색 결과는 자주 바뀌지 않으므로 10분 동안 재사용한다. */
const SEARCH_CACHE_TTL_MS = 10 * 60_000;
const TAXI_CACHE_TTL_MS = 5 * 60_000;
/** 단기예보는 3시간마다 발표되므로 같은 격자·발표분은 한 번만 받는다. */
const WEATHER_CACHE_TTL_MS = 3 * 60 * 60_000;
/**
 * 실시간 도착 정보는 출발이 이만큼 가까울 때만 쓴다. 그보다 먼 출발에 "지금 3분 후 도착"을 붙이면
 * 사용자가 실제로 탈 차가 아니라서 오히려 틀린 안내가 된다.
 */
const REALTIME_WINDOW_MS = 30 * 60_000;
/** 실시간 조회는 짧게만 재사용한다. */
const REALTIME_CACHE_TTL_MS = 30_000;
const TAGO_NODE_CACHE_TTL_MS = 24 * 60 * 60 * 1000;
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
  const staleRoutes = new TtlCache<OdsayResponse>(STALE_ROUTE_TTL_MS, 2_000, clock);
  const odsayPerIp = new KeyedDailyQuota(limits.odsayPerIpDailyLimit, clock);
  let saverLogged = -1;
  /** 절약 모드: 오늘 ODsay 사용량이 상한의 80% 이상 */
  function saverMode(): boolean {
    const on = odsayQuota.nearLimit(SAVER_MODE_RATIO);
    const day = Math.floor((clock() + 9 * 3_600_000) / 86_400_000);
    if (on && saverLogged !== day) {
      saverLogged = day;
      console.error(`[proxy] ODsay 절약 모드: 오늘 ${odsayQuota.usedToday()}/${odsayQuota.limit} 사용`);
    }
    return on;
  }
  // 경로는 1분만 캐시하지만(배차·실시간 반영), 노선 모양은 바뀌지 않아 하루 둔다. 같은 경로를 다시 조회할 때 ODsay 호출이 1번으로 줄어든다.
  const geometryCache = new TtlCache<Array<Array<[number, number]>>>(GEOMETRY_CACHE_TTL_MS, 2_000, clock);
  const searchCache = new TtlCache<PlaceSearchResponseDto>(SEARCH_CACHE_TTL_MS, 1_000, clock);
  const taxiCache = new TtlCache<TaxiEstimateDto | null>(TAXI_CACHE_TTL_MS, 1_000, clock);
  const weatherCache = new TtlCache<Forecast>(WEATHER_CACHE_TTL_MS, 500, clock);
  const weatherQuota = new DailyQuota(limits.weatherDailyLimit, clock);
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
    const key = routeCacheKey(coords);
    let raw = routeCache.get(key);
    let stale = false;
    if (!raw) {
      const saved = staleRoutes.get(key);
      const useSaved = (reason: string) => {
        console.error(`[proxy] 예비 경로 캐시로 응답: ${reason}`);
        raw = saved;
        stale = true;
      };
      if (saved && saverMode()) {
        useSaved("절약 모드");
      } else if (!odsayPerIp.take(clientIp(req.headers, req.socket.remoteAddress))) {
        // 한 IP 가 하루 몫을 다 썼다. 전체 한도는 남겨 두고 예비 캐시가 있으면 그것으로 답한다.
        if (!saved) {
          console.error(`[proxy] 429 IP 별 ODsay 하루 상한(${odsayPerIp.limit}) 도달`);
          res.set("Retry-After", String(odsayQuota.secondsUntilReset())).status(429).json({ error: "daily limit reached" });
          return;
        }
        useSaved("IP 별 하루 상한 도달");
      } else if (!odsayQuota.take()) {
        if (!saved) {
          console.error(`[proxy] 429 ODsay 하루 호출 상한(${odsayQuota.limit}) 도달`);
          res.set("Retry-After", String(odsayQuota.secondsUntilReset())).status(429).json({ error: "daily limit reached" });
          return;
        }
        useSaved("하루 상한 도달");
      } else {
        try {
          const fetched = await fetchOdsayRaw(config.odsayApiKey, coords, config.fetchImpl);
          // 오류 응답(키 오류·ODsay 자체 한도 초과 등)이면 여기서 예외가 나므로 캐시에는 정상 응답만 들어간다.
          toRouteResponse(fetched, departAt);
          // 지도용 실제 노선 모양. 같은 하루 상한을 나눠 쓰고, 절약 모드에서는 부르지 않는다(지도는 정류장 좌표로 그린다).
          if (fetched.error === undefined) {
            await attachLaneGeometry(config.odsayApiKey, fetched, () => !saverMode() && odsayQuota.take(), config.fetchImpl, geometryCache);
          }
          routeCache.set(key, fetched);
          staleRoutes.set(key, fetched);
          raw = fetched;
        } catch (e) {
          if (!saved) throw e;
          useSaved((e as Error).message);
        }
      }
    }
    const route = raw as OdsayResponse;
    const body = realtime.applies(departAt) ? await realtime.adjust(route, departAt) : toRouteResponse(route, departAt);
    res.json(stale ? { ...body, realtime: false } : body);
  });

  // 오늘(한국 시간) 외부 API 사용량. 키·요청 내용은 싣지 않는다.
  app.get("/v1/usage", (req, res) => {
    if (rejectIfLimited(req, res, searchLimiter)) return;
    const count = (q: DailyQuota) => ({ used: q.usedToday(), limit: q.limit });
    res.json({
      saver_mode: saverMode(),
      resets_in_seconds: odsayQuota.secondsUntilReset(),
      odsay: count(odsayQuota),
      kakao: count(kakaoQuota),
      weather: count(weatherQuota),
      realtime: Object.fromEntries(Object.entries(realtime.quotas).map(([name, q]) => [name, count(q)])),
    });
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

  // 지각 위험일 때 앱이 보여 줄 택시 예상 시간·요금. 같은 출발·도착은 5분 동안 캐시한다.
  app.get("/v1/taxi/estimate", async (req, res) => {
    if (rejectIfLimited(req, res, searchLimiter)) return;
    const originLat = latitude(req.query.origin_lat);
    const originLng = longitude(req.query.origin_lng);
    const destinationLat = latitude(req.query.destination_lat);
    const destinationLng = longitude(req.query.destination_lng);
    if (originLat === null || originLng === null || destinationLat === null || destinationLng === null) {
      res.status(400).json({ error: "origin_lat, origin_lng, destination_lat, destination_lng 가 올바른 좌표여야 합니다." });
      return;
    }
    const key = [originLat, originLng, destinationLat, destinationLng].map((v) => v.toFixed(4)).join(",");
    const cached = taxiCache.get(key);
    if (cached !== undefined) {
      if (cached) res.json(cached);
      else res.status(404).json({ error: "no route" });
      return;
    }
    if (rejectIfQuotaExceeded(res, kakaoQuota, "Kakao")) return;
    const estimate = await fetchTaxiEstimate(config.kakaoRestApiKey, { originLat, originLng, destinationLat, destinationLng }, config.fetchImpl);
    taxiCache.set(key, estimate);
    if (estimate) res.json(estimate);
    else res.status(404).json({ error: "no route" });
  });

  // 출발 시각 날씨(기상청 단기예보). 같은 격자·발표분은 3시간 캐시한다.
  app.get("/v1/weather", async (req, res) => {
    if (rejectIfLimited(req, res, searchLimiter)) return;
    const lat = latitude(req.query.lat);
    const lng = longitude(req.query.lng);
    const at = typeof req.query.at === "string" ? Date.parse(req.query.at) : Number.NaN;
    if (lat === null || lng === null || Number.isNaN(at)) {
      res.status(400).json({ error: "lat, lng 는 좌표, at 은 ISO-8601 시각이어야 합니다." });
      return;
    }
    const key = config.realtimeKeys?.dataGoKrKey;
    if (!key) {
      res.status(503).json({ error: "weather unavailable" });
      return;
    }
    const grid = toGrid(lat, lng);
    const base = latestBase(clock());
    const cacheKey = `${grid.nx},${grid.ny},${base.baseDate}${base.baseTime}`;
    let forecast = weatherCache.get(cacheKey);
    if (!forecast) {
      if (rejectIfQuotaExceeded(res, weatherQuota, "기상청")) return;
      forecast = (await fetchForecast(key, grid, base, config.fetchImpl)) ?? undefined;
      if (!forecast) {
        res.status(502).json({ error: "upstream error" });
        return;
      }
      weatherCache.set(cacheKey, forecast);
    }
    const weather = weatherAt(forecast, at);
    if (weather) res.json(weather);
    else res.status(404).json({ error: "out of forecast range" });
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
    // TAGO 는 정류소 찾기·도착 조회 두 API 가 따로 한도를 갖는다.
    tagoStop: new DailyQuota(dailyLimit, clock),
    tago: new DailyQuota(dailyLimit, clock),
  };
  // 정류장 좌표 → TAGO 정류소. 정류소는 거의 바뀌지 않아 하루 동안 기억한다(찾지 못한 경우도).
  const tagoNodes = new TtlCache<{ node: TagoNode | null }>(TAGO_NODE_CACHE_TTL_MS, 2000, clock);

  async function tagoNode(key: string, query: TagoBusQuery): Promise<TagoNode | null> {
    const cacheKey = `${query.lat.toFixed(5)},${query.lng.toFixed(5)},${query.stopName}`;
    const hit = tagoNodes.get(cacheKey);
    if (hit) return hit.node;
    if (!quotas.tagoStop.take()) return null;
    const node = await findTagoNode(key, query, fetchImpl);
    // 응답을 읽지 못한 경우(undefined)는 기억하지 않고 다음에 다시 찾는다.
    if (node !== undefined) tagoNodes.set(cacheKey, { node });
    return node ?? null;
  }
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
    } else if (target.query.region === "tago") {
      if (!keys.dataGoKrKey) return null;
      const node = await tagoNode(keys.dataGoKrKey, target.query);
      if (!node || !quotas.tago.take()) return null;
      result = await fetchTagoArrivals(keys.dataGoKrKey, node, target.query.routeNo, fetchImpl);
    } else {
      if (!keys.dataGoKrKey || !quotas[target.query.region].take()) return null;
      result = await fetchBusArrivals(keys.dataGoKrKey, target.query, fetchImpl);
    }
    cache.set(cacheKey, { list: result, at: clock() });
    return result;
  }

  return {
    quotas,

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

/**
 * 경로 캐시 키. 좌표를 약 50m(0.0005도) 단위로 묶는다. 현재 위치 출발처럼 좌표가 조금씩 흔들려도
 * 같은 캐시를 쓰게 해 ODsay 호출을 줄인다. 50m 안의 출발지는 경로와 걷는 시간이 거의 같다.
 */
export function routeCacheKey(c: { originLat: number; originLng: number; destinationLat: number; destinationLng: number }): string {
  return [c.originLat, c.originLng, c.destinationLat, c.destinationLng]
    .map((v) => (Math.round(v * ROUTE_KEY_STEPS_PER_DEGREE) / ROUTE_KEY_STEPS_PER_DEGREE).toFixed(4))
    .join(",");
}

const ROUTE_KEY_STEPS_PER_DEGREE = 2_000;

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
