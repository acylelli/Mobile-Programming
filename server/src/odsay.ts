import { type RouteDto, type RouteResponseDto, type SegmentDto, toKstIso, UpstreamError } from "./contract.ts";
import type { BusQuery, SubwayQuery } from "./realtime.ts";

/*
 * ODsay 대중교통 길찾기(searchPubTransPathT) → 앱 경로 계약 변환.
 *
 * - ODsay 는 출발 시각을 받지 않고 "지금 기준" 경로와 소요시간을 준다.
 *   그래서 departure_time 은 앱이 요청한 depart_at 을 그대로 쓰고 arrival_time = 출발 + 총 소요시간으로 계산한다.
 * - 처음·마지막 도보는 WALK 구간으로 내려주고, 탈것 사이의 도보는 환승 시간(transfer_minutes)으로 합친다.
 *   앱 노선도는 탈것 두 개가 연달아 있으면 그 사이를 환승으로 그리기 때문이다.
 * - ODsay 총 소요시간과 구간 합의 차이는 대기 시간(wait_minutes)으로 둔다.
 * - 평소 대비 지연(delay_minutes)은 ODsay 시간표 기준이라 0 이다. 출발 30분 안이면 실시간 도착으로 다시 계산한다(realtime.ts).
 * - 출발이 가까우면 app.ts 가 첫 탑승 구간의 실시간 도착 정보로 대기 시간과 도착 안내를 다시 채운다(realtime.ts).
 */

const ODSAY_ENDPOINT = "https://api.odsay.com/v1/api/searchPubTransPathT";
const LOAD_LANE_ENDPOINT = "https://api.odsay.com/v1/api/loadLane";
/** 실제 노선 모양은 앱이 지도에 그리는 상위 경로에만 붙인다(경로마다 ODsay 호출이 하나 더 든다). */
/** 노선 모양은 지도에 그리는 가장 빠른 경로 하나만 받는다(경로마다 loadLane 1회). */
export const GEOMETRY_ROUTES = 1;
const MAX_ROUTES = 5;

/** ODsay trafficType */
const SUBWAY = 1;
const BUS = 2;
const WALK = 3;
/** ODsay busCityCode: 서울 */
const SEOUL_CITY_CODE = 1000;

export interface OdsaySubPath {
  trafficType: number;
  sectionTime: number;
  startName?: string;
  endName?: string;
  lane?: Array<{ name?: string; busNo?: string; busLocalBlID?: string; busCityCode?: number; subwayCode?: number }>;
  /** 배차 간격(분) */
  intervalTime?: number;
  /** 서울 버스 정류장 고유번호(예: "08718") */
  startArsID?: string;
  /** 지역 버스 정보 시스템의 정류장 ID(경기: 9자리) */
  startLocalStationID?: string;
  /** 승차 정류장 좌표(경도·위도) */
  startX?: number | string;
  startY?: number | string;
  passStopList?: { stations?: Array<{ stationName?: string; x?: string; y?: string }> };
}

export interface OdsayPath {
  info: { totalTime: number; mapObj?: string };
  subPath: OdsaySubPath[];
  /**
   * 탑승 구간마다 실제 노선 모양 좌표 [위도, 경도](ODsay loadLane). 서버가 붙여 캐시에 함께 둔다.
   * 없거나 탑승 구간 수와 맞지 않으면 정류장 좌표를 쓴다.
   */
  laneGeometry?: Array<Array<[number, number]>>;
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

/** 노선 모양 캐시. 같은 노선(mapObj)의 모양은 바뀌지 않으므로 경로 캐시보다 훨씬 오래 둔다. */
export interface LaneGeometryCache {
  get(mapObj: string): Array<Array<[number, number]>> | undefined;
  set(mapObj: string, lanes: Array<Array<[number, number]>>): void;
}

/**
 * 소요시간이 짧은 상위 경로에 실제 노선 모양을 붙인다. 실패해도 경로 조회는 그대로 진행한다(정류장 좌표로 대체).
 * [allow] 는 ODsay 를 실제로 부를 때마다 하루 상한을 확인한다. [cache] 에 있으면 부르지 않는다.
 */
export async function attachLaneGeometry(
  apiKey: string,
  body: OdsayResponse,
  allow: () => boolean,
  fetchImpl: typeof fetch = fetch,
  cache?: LaneGeometryCache,
): Promise<void> {
  const paths = [...(body.result?.path ?? [])]
    .filter((p) => p.info?.mapObj)
    .sort((a, b) => a.info.totalTime - b.info.totalTime)
    .slice(0, GEOMETRY_ROUTES);
  await Promise.all(
    paths.map(async (path) => {
      const mapObj = path.info.mapObj!;
      const cached = cache?.get(mapObj);
      if (cached) {
        path.laneGeometry = cached;
        return;
      }
      if (!allow()) return;
      const lanes = await fetchLaneGeometry(apiKey, mapObj, fetchImpl);
      if (lanes) {
        path.laneGeometry = lanes;
        cache?.set(mapObj, lanes);
      }
    }),
  );
}

/** ODsay loadLane: 탑승 구간(lane) 순서대로 좌표를 돌려준다. 실패하면 null */
export async function fetchLaneGeometry(
  apiKey: string,
  mapObj: string,
  fetchImpl: typeof fetch = fetch,
): Promise<Array<Array<[number, number]>> | null> {
  const params = new URLSearchParams({ mapObject: `0:0@${mapObj}`, apiKey });
  try {
    const response = await fetchImpl(`${LOAD_LANE_ENDPOINT}?${params}`, { signal: AbortSignal.timeout(5_000) });
    if (!response.ok) return null;
    return parseLaneGeometry(await response.json());
  } catch {
    return null;
  }
}

interface LoadLaneResponse {
  result?: { lane?: Array<{ section?: Array<{ graphPos?: Array<{ x?: number; y?: number }> }> }> };
}

export function parseLaneGeometry(body: unknown): Array<Array<[number, number]>> | null {
  const lanes = (body as LoadLaneResponse)?.result?.lane;
  if (!Array.isArray(lanes) || lanes.length === 0) return null;
  return lanes.map((lane) =>
    (lane.section ?? []).flatMap((section) =>
      (section.graphPos ?? [])
        .filter((p) => Number.isFinite(p.x) && Number.isFinite(p.y))
        .map((p): [number, number] => [round6(p.y!), round6(p.x!)]),
    ),
  );
}

export function toRouteResponse(body: OdsayResponse, departAt: Date): RouteResponseDto {
  if (body.error !== undefined) {
    const { code, message } = readError(body.error);
    if (NO_ROUTE_CODES.has(code)) return { realtime: true, routes: [] };
    // 키 오류·호출 한도 초과 등은 앱 쪽 문제가 아니므로 502 로 돌려주고 앱은 캐시로 대체한다.
    throw new UpstreamError(502, `ODsay 오류 ${code}: ${message}`);
  }
  // ODsay 는 요청 시점에 바로 계산한 결과다. 앱의 realtime=false 는 "캐시로 대체한 값"을 뜻하므로 true 로 둔다.
  return { realtime: true, routes: toRoutesWithPaths(body, departAt).map((r) => r.route) };
}

/** 경로와 그 경로의 ODsay 원본을 함께 돌려준다. 실시간 보정에 원본의 정류장·노선 ID 가 필요하다. */
export function toRoutesWithPaths(body: OdsayResponse, departAt: Date): Array<{ route: RouteDto; path: OdsayPath }> {
  return (body.result?.path ?? [])
    .map((path) => ({ route: toRoute(path, departAt), path }))
    .filter((r): r is { route: RouteDto; path: OdsayPath } => r.route !== null)
    .sort((a, b) => a.route.total_minutes - b.route.total_minutes)
    .slice(0, MAX_ROUTES);
}

export type RideTarget = (
  | { kind: "subway"; query: SubwayQuery }
  | { kind: "bus"; query: BusQuery }
) & {
  /** ODsay 배차 간격(분). 실시간 목록 밖의 차를 추정할 때 쓴다. */
  headwayMinutes?: number;
};

/** 첫 탑승 구간의 실시간 조회 대상. 조회할 수 없는 지역·노선이면 null */
export function firstRideTarget(path: OdsayPath): RideTarget | null {
  const ride = path.subPath.find((s) => s.trafficType !== WALK);
  const lane = ride?.lane?.[0];
  if (!ride || !lane) return null;
  const headwayMinutes = ride.intervalTime && ride.intervalTime > 0 ? ride.intervalTime : undefined;
  const target = rideTarget(ride, lane);
  return target ? { ...target, ...(headwayMinutes ? { headwayMinutes } : {}) } : null;
}

function rideTarget(ride: OdsaySubPath, lane: NonNullable<OdsaySubPath["lane"]>[number]): RideTarget | null {
  if (ride.trafficType === SUBWAY) {
    const station = ride.startName;
    const nextStation = ride.passStopList?.stations?.[1]?.stationName;
    if (!station || !nextStation) return null;
    return { kind: "subway", query: { station, nextStation, odsayLineCode: lane.subwayCode } };
  }
  if (ride.trafficType === BUS && lane.busLocalBlID) {
    if (lane.busCityCode === SEOUL_CITY_CODE && ride.startArsID) {
      return { kind: "bus", query: { region: "seoul", stationKey: ride.startArsID.replace(/-/g, ""), routeId: lane.busLocalBlID } };
    }
    // 경기 버스 노선·정류소 ID 는 2 로 시작하는 9자리다.
    if (/^2\d{8}$/.test(lane.busLocalBlID) && ride.startLocalStationID) {
      return { kind: "bus", query: { region: "gyeonggi", stationKey: ride.startLocalStationID, routeId: lane.busLocalBlID } };
    }
  }
  // 그 밖의 지역 버스는 승차 정류장 좌표와 노선 번호로 TAGO 에서 찾는다(서울 노선은 위에서 처리했거나 정보가 모자란 경우라 제외).
  if (ride.trafficType === BUS && lane.busNo && lane.busCityCode !== SEOUL_CITY_CODE && !/^2\d{8}$/.test(lane.busLocalBlID ?? "")) {
    const first = ride.passStopList?.stations?.[0];
    const lat = Number(ride.startY ?? first?.y);
    const lng = Number(ride.startX ?? first?.x);
    const stopName = ride.startName ?? first?.stationName;
    if (Number.isFinite(lat) && Number.isFinite(lng) && lat !== 0 && stopName) {
      return { kind: "bus", query: { region: "tago", lat, lng, stopName, routeNo: lane.busNo } };
    }
  }
  return null;
}

function toRoute(path: OdsayPath, departAt: Date): RouteDto | null {
  const subPaths = path.subPath ?? [];
  const firstRide = subPaths.findIndex((s) => s.trafficType !== WALK);
  if (firstRide < 0) return null; // 도보만 있는 경로는 대중교통 알람 대상이 아니다.
  const lastRide = subPaths.length - 1 - [...subPaths].reverse().findIndex((s) => s.trafficType !== WALK);

  const segments: SegmentDto[] = [];
  const rides = subPaths.filter((s) => s.trafficType !== WALK).length;
  const geometry = path.laneGeometry?.length === rides ? path.laneGeometry : undefined;
  let rideIndex = 0;
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
      ...withPoints(sub, geometry?.[rideIndex]),
    });
    rideIndex += 1;
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

/**
 * 지도 경로선 좌표. 실제 노선 모양(loadLane)이 있으면 그것을, 없으면 지나는 정류장·역 좌표를 쓴다.
 * 좌표가 둘 미만이면 선을 그릴 수 없어 넣지 않는다.
 */
function withPoints(sub: OdsaySubPath, lane?: Array<[number, number]>): { points?: Array<[number, number]> } {
  if (lane && lane.length >= 2) return { points: lane };
  const points: Array<[number, number]> = [];
  for (const station of sub.passStopList?.stations ?? []) {
    const lat = Number(station.y);
    const lng = Number(station.x);
    if (station.x === undefined || station.y === undefined || !Number.isFinite(lat) || !Number.isFinite(lng)) continue;
    points.push([round6(lat), round6(lng)]);
  }
  return points.length >= 2 ? { points } : {};
}

function round6(value: number): number {
  return Math.round(value * 1e6) / 1e6;
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
