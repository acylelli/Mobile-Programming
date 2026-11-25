import { type RouteDto, toKstIso } from "./contract.ts";


/*
 * 실시간 도착 정보.
 *
 * - 지하철: 서울 열린데이터광장 실시간 지하철 도착정보(역 이름으로 조회, 수도권 전 노선)
 * - 서울 버스: 공공데이터포털 서울특별시_버스도착정보조회(정류장 고유번호 arsId)
 * - 경기 버스: 공공데이터포털 경기도_버스도착정보 조회(정류소 ID)
 *
 * ODsay 경로 응답에 각 API 가 요구하는 역 이름·정류장 ID·노선 ID 가 들어 있어서 그대로 이어 붙인다.
 * 모든 조회는 실패해도 예외를 던지지 않고 null 을 돌려준다. 실시간 정보가 없어도 경로 조회는 성공해야 한다.
 */

/** 앞으로 들어올 차량 하나. seconds 는 지금부터 도착까지 남은 초 */
export interface Arrival {
  /** 지금부터 도착까지 남은 초. 이미 떠난 차는 음수 */
  seconds: number;
  /** 실시간 목록에 없어서 배차 간격으로 추정한 차 */
  estimated?: boolean;
}

export interface RealtimeKeys {
  /** 서울 열린데이터광장 실시간 지하철 인증키 */
  seoulSubwayKey?: string;
  /** 공공데이터포털 일반 인증키(Decoding) */
  dataGoKrKey?: string;
}

export interface SubwayQuery {
  /** ODsay 역 이름("다산"). "역"은 붙지 않는다. */
  station: string;
  /** 다음 역 이름. 방향을 가리는 데 쓴다("별내행 - 별내방면"). */
  nextStation: string;
  /** ODsay subwayCode */
  odsayLineCode?: number;
}

export interface BusQuery {
  region: "seoul" | "gyeonggi";
  /** 서울: 정류장 고유번호(arsId), 경기: 정류소 ID */
  stationKey: string;
  /** 서울·경기 노선 ID (ODsay busLocalBlID) */
  routeId: string;
}

const TIMEOUT_MS = 4_000;

/** ODsay subwayCode → 서울 실시간 지하철 subwayId */
const SUBWAY_IDS: Record<number, string> = {
  1: "1001", 2: "1002", 3: "1003", 4: "1004", 5: "1005", 6: "1006", 7: "1007", 8: "1008", 9: "1009",
  101: "1065", // 공항철도
  104: "1063", // 경의중앙선
  108: "1067", // 경춘선
  109: "1077", // 신분당선
  113: "1092", // 우이신설선
  114: "1093", // 서해선
  116: "1075", // 수인분당선
};

export async function fetchSubwayArrivals(
  key: string,
  query: SubwayQuery,
  fetchImpl: typeof fetch = fetch,
  now: number = Date.now(),
): Promise<Arrival[] | null> {
  // 이 API 는 http 만 제공한다. 키 외에 개인 정보는 보내지 않는다.
  const url = `http://swopenapi.seoul.go.kr/api/subway/${encodeURIComponent(key)}/json/realtimeStationArrival/0/30/${encodeURIComponent(query.station)}`;
  const body = await getJson(url, fetchImpl);
  return body ? logIfUnreadable(parseSubwayArrivals(body, query, now), body, "subway") : null;
}

export function parseSubwayArrivals(body: unknown, query: SubwayQuery, now: number = Date.now()): Arrival[] | null {
  const list = (body as { realtimeArrivalList?: unknown }).realtimeArrivalList;
  if (!Array.isArray(list)) return null;
  const subwayId = query.odsayLineCode === undefined ? undefined : SUBWAY_IDS[query.odsayLineCode];
  const arrivals: Arrival[] = [];
  for (const item of list as Array<Record<string, unknown>>) {
    if (subwayId && String(item.subwayId) !== subwayId) continue;
    // "별내행 - 별내방면" 처럼 다음 역 이름으로 방향을 가린다.
    if (!String(item.trainLineNm ?? "").includes(`${query.nextStation}방면`)) continue;
    const seconds = subwaySeconds(item);
    if (seconds !== null) arrivals.push({ seconds: seconds - secondsSince(item.recptnDt, now) });
  }
  return sortArrivals(arrivals);
}

/** barvlDt(초)가 0 인 노선이 많아서 안내 문구("3분 20초 후", "전역 도착")로 보충한다. */
function subwaySeconds(item: Record<string, unknown>): number | null {
  const barvl = Number(item.barvlDt);
  if (Number.isFinite(barvl) && barvl > 0) return barvl;
  const message = String(item.arvlMsg2 ?? "");
  const match = message.match(/(\d+)\s*분(?:\s*(\d+)\s*초)?/);
  if (match) return Number(match[1]) * 60 + Number(match[2] ?? 0);
  if (/당역 (도착|진입)|진입|도착/.test(message) && !message.includes("전역")) return 0;
  // "다산 출발" — 이미 떠난 열차. 탈 수는 없지만 다음 열차를 추정하는 기준이 된다.
  if (message.includes("출발") && !message.includes("전역")) return -30;
  if (message.includes("전역 도착") || message.includes("전역 진입") || message.includes("전역 출발")) return 120;
  // "[3]번째 전역 (구리)" — 역 사이를 2분으로 어림한다.
  const stations = message.match(/\[(\d+)\]번째 전역/);
  if (stations) return Number(stations[1]) * 120;
  return null;
}

export async function fetchBusArrivals(key: string, query: BusQuery, fetchImpl: typeof fetch = fetch): Promise<Arrival[] | null> {
  if (query.region === "seoul") {
    const params = new URLSearchParams({ serviceKey: key, arsId: query.stationKey, resultType: "json" });
    const body = await getJson(`http://ws.bus.go.kr/api/rest/stationinfo/getStationByUid?${params}`, fetchImpl);
    return body ? logIfUnreadable(parseSeoulBusArrivals(body, query.routeId), body, "seoul-bus") : null;
  }
  const params = new URLSearchParams({ serviceKey: key, stationId: query.stationKey, format: "json" });
  const body = await getJson(`https://apis.data.go.kr/6410000/busarrivalservice/v2/getBusArrivalListv2?${params}`, fetchImpl);
  return body ? logIfUnreadable(parseGyeonggiBusArrivals(body, query.routeId), body, "gyeonggi-bus") : null;
}

export function parseSeoulBusArrivals(body: unknown, routeId: string): Arrival[] | null {
  const items = asArray((body as { msgBody?: { itemList?: unknown } }).msgBody?.itemList);
  if (!items) return null;
  const item = items.find((i) => String(i.busRouteId) === routeId);
  if (!item) return [];
  const arrivals: Arrival[] = [];
  for (const n of [1, 2]) {
    const seconds = Number(item[`traTime${n}`]);
    // 운행 종료·출발 대기 등은 traTime 이 0 이고 안내 문구만 있다.
    if (Number.isFinite(seconds) && seconds > 0) arrivals.push({ seconds });
    else if (String(item[`arrmsg${n}`] ?? "").startsWith("곧 도착")) arrivals.push({ seconds: 30 });
  }
  return sortArrivals(arrivals);
}

export function parseGyeonggiBusArrivals(body: unknown, routeId: string): Arrival[] | null {
  const msgBody = (body as { response?: { msgBody?: { busArrivalList?: unknown } } }).response?.msgBody;
  if (!msgBody) return null;
  const items = asArray(msgBody.busArrivalList) ?? [];
  const item = items.find((i) => String(i.routeId) === routeId);
  if (!item) return [];
  const arrivals: Arrival[] = [];
  for (const n of [1, 2]) {
    const sec = Number(item[`predictTimeSec${n}`]);
    const min = Number(item[`predictTime${n}`]);
    if (Number.isFinite(sec) && sec > 0) arrivals.push({ seconds: sec });
    else if (item[`predictTime${n}`] !== "" && item[`predictTime${n}`] != null && Number.isFinite(min) && min >= 0) arrivals.push({ seconds: min * 60 });
  }
  return sortArrivals(arrivals);
}

/**
 * 응답은 왔지만 도착 목록이 없을 때(인증키 오류, 해외 IP 차단, 호출 한도 등) 원인을 로그에 남긴다.
 * 각 API 의 오류 코드·메시지 필드만 골라 쓰고, 요청 주소(키 포함)는 남기지 않는다.
 */
export function describeUnreadable(body: unknown): string {
  const b = (body ?? {}) as Record<string, any>;
  const candidates = [
    b.errorMessage, // 서울 지하철
    b, // 서울 지하철·data.go.kr 게이트웨이 오류 {code, message} / {error, message}
    b.msgHeader, // 서울 버스
    b.response?.msgHeader, // 경기 버스
  ];
  for (const c of candidates) {
    if (!c || typeof c !== "object") continue;
    const code = c.code ?? c.headerCd ?? c.resultCode ?? c.error ?? c.status;
    const message = c.message ?? c.headerMsg ?? c.resultMessage;
    if (code !== undefined || message !== undefined) return `${String(code ?? "")} ${String(message ?? "")}`.trim().slice(0, 200);
  }
  return JSON.stringify(body).slice(0, 200);
}

function logIfUnreadable(result: Arrival[] | null, body: unknown, source: string): Arrival[] | null {
  if (result === null) console.error(`[realtime] ${source} 응답에 도착 목록 없음: ${describeUnreadable(body)}`);
  return result;
}

/**
 * 지하철 도착 정보는 수신 시각(recptnDt, 한국 시간)을 함께 준다. 몇 분 지난 정보가 오기도 해서
 * 그만큼 남은 시간을 줄인다. 형식이 이상하거나 시계가 어긋나 미래 시각이면 보정하지 않는다.
 */
function secondsSince(recptnDt: unknown, now: number): number {
  const match = String(recptnDt ?? "").match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2})/);
  if (!match) return 0;
  const received = Date.parse(`${match[1]}T${match[2]}+09:00`);
  if (!Number.isFinite(received)) return 0;
  return Math.min(Math.max(0, (now - received) / 1000), 600);
}

function sortArrivals(arrivals: Arrival[]): Arrival[] {
  return arrivals.sort((a, b) => a.seconds - b.seconds);
}

/** 항목이 하나뿐이면 배열 대신 객체로 오는 API 가 있다. */
function asArray(value: unknown): Array<Record<string, unknown>> | null {
  if (value == null) return null;
  return (Array.isArray(value) ? value : [value]) as Array<Record<string, unknown>>;
}

async function getJson(url: string, fetchImpl: typeof fetch): Promise<unknown | null> {
  try {
    const response = await fetchImpl(url, { signal: AbortSignal.timeout(TIMEOUT_MS) });
    if (!response.ok) {
      console.error(`[realtime] HTTP ${response.status} ${new URL(url).hostname}`);
      return null;
    }
    return await response.json();
  } catch (e) {
    // 인증키가 틀리면 JSON 대신 XML 오류가 와서 여기로 온다. URL 에 키가 있으므로 호스트만 남긴다.
    console.error(`[realtime] ${(e as Error).name} ${new URL(url).hostname}`);
    return null;
  }
}

/** 목록에 탈 수 있는 차가 없을 때 배차 간격으로 몇 대 뒤까지 추정할지. 그보다 멀면 추정이 부정확하다. */
const MAX_ESTIMATED_STEPS = 3;

/**
 * 사용자가 정류장에 닿는 시각 이후 처음 오는 차량.
 * 실시간 API 는 방향마다 다음 차 2대 정도만 알려 준다. 그 안에 탈 수 있는 차가 없으면
 * 목록의 마지막 차에 배차 간격(headwayMinutes)을 더해 추정한다.
 */
export function firstCatchable(arrivals: Arrival[], secondsUntilAtStop: number, headwayMinutes?: number): Arrival | null {
  const known = arrivals.find((a) => a.seconds >= secondsUntilAtStop);
  if (known) return known;
  if (!headwayMinutes || headwayMinutes <= 0 || arrivals.length === 0) return null;
  const step = headwayMinutes * 60;
  const last = Math.max(...arrivals.map((a) => a.seconds));
  const steps = Math.max(1, Math.ceil((secondsUntilAtStop - last) / step));
  if (steps > MAX_ESTIMATED_STEPS) return null;
  return { seconds: last + steps * step, estimated: true };
}

/**
 * 첫 탑승 구간에 실시간 도착을 반영한다.
 * - 대기 시간 = 정류장에 닿은 뒤 탈 수 있는 차가 올 때까지
 * - 첫 탑승 구간에 "N분 후 도착" 안내 (지금 기준, 사용자가 탈 수 있는 차)
 * - 총 소요시간과 도착 시각을 다시 계산한다
 */
export function applyArrival(route: RouteDto, secondsUntilAtStop: number, arrival: Arrival): RouteDto {
  const firstRide = route.segments.findIndex((s) => s.mode !== "WALK");
  if (firstRide < 0) return route;
  const waitMinutes = Math.max(0, Math.ceil((arrival.seconds - secondsUntilAtStop) / 60));
  const totalMinutes = route.total_minutes - route.wait_minutes + waitMinutes;
  const departure = Date.parse(route.departure_time);
  const minutesFromNow = Math.ceil(arrival.seconds / 60);
  const segments = route.segments.map((s, i) =>
    i === firstRide ? { ...s, arrival_message: arrivalMessage(minutesFromNow, arrival.estimated === true) } : s,
  );
  return {
    ...route,
    wait_minutes: waitMinutes,
    total_minutes: totalMinutes,
    arrival_time: toKstIso(new Date(departure + totalMinutes * 60_000)),
    segments,
  };
}

/** 실시간으로 확인한 차는 "7분 후 도착", 배차 간격으로 추정한 차는 "약 7분 후 도착" */
function arrivalMessage(minutesFromNow: number, estimated: boolean): string {
  if (estimated) return `약 ${Math.max(1, minutesFromNow)}분 후 도착`;
  return minutesFromNow <= 1 ? "곧 도착" : `${minutesFromNow}분 후 도착`;
}
