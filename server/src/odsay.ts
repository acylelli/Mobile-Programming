import { type RouteDto, type RouteResponseDto, type SegmentDto, toKstIso, UpstreamError } from "./contract.ts";

/*
 * ODsay 대중교통 길찾기(searchPubTransPathT) → 앱 경로 계약 변환.
 *
 * - ODsay 는 출발 시각을 받지 않고 "지금 기준" 경로와 소요시간을 준다.
 *   그래서 departure_time 은 앱이 요청한 depart_at 을 그대로 쓰고 arrival_time = 출발 + 총 소요시간으로 계산한다.
 * - 처음·마지막 도보는 WALK 구간으로 내려주고, 탈것 사이의 도보는 환승 시간(transfer_minutes)으로 합친다.
 *   앱 노선도는 탈것 두 개가 연달아 있으면 그 사이를 환승으로 그리기 때문이다.
 * - ODsay 총 소요시간과 구간 합의 차이는 대기 시간(wait_minutes)으로 둔다.
 * - 실시간 지연(delay_minutes)은 ODsay 길찾기 응답에 없어서 0 으로 둔다(실시간 도착 API 연동 시 채운다).
 */

const ODSAY_ENDPOINT = "https://api.odsay.com/v1/api/searchPubTransPathT";
const MAX_ROUTES = 5;

/** ODsay trafficType */
const SUBWAY = 1;
const BUS = 2;
const WALK = 3;

export interface OdsaySubPath {
  trafficType: number;
  sectionTime: number;
  startName?: string;
  endName?: string;
  lane?: Array<{ name?: string; busNo?: string }>;
}

export interface OdsayPath {
  info: { totalTime: number };
  subPath: OdsaySubPath[];
}

export interface OdsayResponse {
  result?: { path?: OdsayPath[] };
  error?: unknown;
}

export interface RouteQuery {
  originLat: number;
  originLng: number;
  destinationLat: number;
  destinationLng: number;
  departAt: Date;
}

/** 출발·도착이 너무 가깝거나 대중교통 경로가 없을 때 ODsay 가 주는 오류 코드. 앱에는 "경로 없음"으로 전달한다. */
const NO_ROUTE_CODES = new Set(["-98", "-99", "3", "4", "5", "6"]);

/**
 * ODsay 원본 응답을 가져온다. 출발 시각과 무관한 좌표 기준 결과라서 그대로 캐시해 두고
 * 요청마다 [toRouteResponse] 로 출발·도착 시각만 다시 계산할 수 있다.
 */
export async function fetchOdsayRaw(
  apiKey: string,
  query: Omit<RouteQuery, "departAt">,
  fetchImpl: typeof fetch = fetch,
): Promise<OdsayResponse> {
  const params = new URLSearchParams({
    SX: String(query.originLng),
    SY: String(query.originLat),
    EX: String(query.destinationLng),
    EY: String(query.destinationLat),
    apiKey,
  });
  let response: Response;
  try {
    response = await fetchImpl(`${ODSAY_ENDPOINT}?${params}`, { signal: AbortSignal.timeout(8_000) });
  } catch (e) {
    throw new UpstreamError(504, `ODsay 호출 실패: ${(e as Error).name}`);
  }
  if (!response.ok) throw new UpstreamError(502, `ODsay HTTP ${response.status}`);
  return (await response.json()) as OdsayResponse;
}

export function toRouteResponse(body: OdsayResponse, departAt: Date): RouteResponseDto {
  if (body.error !== undefined) {
    const { code, message } = readError(body.error);
    if (NO_ROUTE_CODES.has(code)) return { realtime: true, routes: [] };
    // 키 오류·호출 한도 초과 등은 앱 쪽 문제가 아니므로 502 로 돌려주고 앱은 캐시로 대체한다.
    throw new UpstreamError(502, `ODsay 오류 ${code}: ${message}`);
  }
  const routes = (body.result?.path ?? [])
    .map((path) => toRoute(path, departAt))
    .filter((route): route is RouteDto => route !== null)
    .sort((a, b) => a.total_minutes - b.total_minutes)
    .slice(0, MAX_ROUTES);
  // ODsay 는 요청 시점에 바로 계산한 결과다. 앱의 realtime=false 는 "캐시로 대체한 값"을 뜻하므로 true 로 둔다.
  return { realtime: true, routes };
}

function toRoute(path: OdsayPath, departAt: Date): RouteDto | null {
  const subPaths = path.subPath ?? [];
  const firstRide = subPaths.findIndex((s) => s.trafficType !== WALK);
  if (firstRide < 0) return null; // 도보만 있는 경로는 대중교통 알람 대상이 아니다.
  const lastRide = subPaths.length - 1 - [...subPaths].reverse().findIndex((s) => s.trafficType !== WALK);

  const segments: SegmentDto[] = [];
  let walkMinutes = 0;
  let transferMinutes = 0;
  let rideCount = 0;

  subPaths.forEach((sub, index) => {
    const minutes = Math.max(0, Math.round(sub.sectionTime ?? 0));
    if (sub.trafficType === WALK) {
      if (index > firstRide && index < lastRide) {
        transferMinutes += minutes;
        return;
      }
      walkMinutes += minutes;
      if (minutes === 0) return;
      const isFirst = index < firstRide;
      segments.push({
        mode: "WALK",
        from: isFirst ? "출발지" : stationName(subPaths[lastRide]!, "end"),
        to: isFirst ? stationName(subPaths[firstRide]!, "start") : "도착지",
        minutes,
      });
      return;
    }
    rideCount += 1;
    segments.push({
      mode: sub.trafficType === SUBWAY ? "SUBWAY" : sub.trafficType === BUS ? "BUS" : "ETC",
      line_name: lineName(sub),
      from: stationName(sub, "start"),
      to: stationName(sub, "end"),
      minutes,
    });
  });

  // ODsay 총 소요시간은 구간 시간 합보다 길 때가 있다(승차 대기 등). 그 차이를 대기 시간으로 둬서
  // 계약의 "총 = 도보 + 탑승 + 환승 + 대기" 가 맞도록 한다.
  const sectionSum = segments.reduce((sum, s) => sum + s.minutes, 0) + transferMinutes;
  const totalMinutes = Math.max(sectionSum, Math.round(path.info.totalTime));
  return {
    total_minutes: totalMinutes,
    walk_minutes: walkMinutes,
    wait_minutes: totalMinutes - sectionSum,
    transfer_minutes: transferMinutes,
    transfer_count: Math.max(0, rideCount - 1),
    delay_minutes: 0,
    departure_time: toKstIso(departAt),
    arrival_time: toKstIso(new Date(departAt.getTime() + totalMinutes * 60_000)),
    segments,
  };
}

/** "수도권 2호선" → "2호선". 앱은 "N호선" 이름으로 노선 색을 고른다. 버스는 "76" → "76번". */
export function lineName(sub: OdsaySubPath): string | undefined {
  const lane = sub.lane?.[0];
  if (!lane) return undefined;
  if (sub.trafficType === BUS) return lane.busNo ? busLabel(lane.busNo) : lane.name;
  return lane.name?.replace(/^수도권\s*/, "");
}

/** 앱은 노선명을 그대로 "76번 승차", "76번 27분" 처럼 보여 준다. 이미 "번"으로 끝나면 그대로 둔다. */
function busLabel(busNo: string): string {
  const trimmed = busNo.trim();
  return trimmed.endsWith("번") ? trimmed : `${trimmed}번`;
}

/** 지하철역은 "역"을 붙여 앱의 다른 화면(검색 결과·Fake 데이터)과 표기를 맞춘다. */
function stationName(sub: OdsaySubPath, side: "start" | "end"): string {
  const name = (side === "start" ? sub.startName : sub.endName) ?? "";
  if (sub.trafficType === SUBWAY && name && !name.endsWith("역")) return `${name}역`;
  return name;
}

/** ODsay 오류는 배열/객체 두 형태로 온다. */
function readError(error: unknown): { code: string; message: string } {
  const item = (Array.isArray(error) ? error[0] : error) as Record<string, unknown> | undefined;
  return {
    code: String(item?.code ?? ""),
    message: String(item?.message ?? item?.msg ?? ""),
  };
}
