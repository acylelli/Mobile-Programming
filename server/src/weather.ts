/**
 * 출발 시각 날씨(기상청 단기예보). 앱이 비·눈 오는 날 여유 시간을 늘리고 "우산 챙기세요"를 알리는 데 쓴다.
 *
 * - 공공데이터포털 "기상청_단기예보 조회서비스"(getVilageFcst). 실시간 도착 정보와 같은 DATA_GO_KR_SERVICE_KEY 를 쓴다.
 * - 위경도를 기상청 5km 격자(nx, ny)로 바꾸고, 가장 최근 발표분에서 출발 시각이 속한 1시간의 값을 고른다.
 * - 단기예보는 하루 8번(02·05·08·11·14·17·20·23시) 발표되고, 발표 10분 뒤부터 조회된다. 약 3일 뒤까지 있다.
 */

export type Precipitation = "NONE" | "RAIN" | "RAIN_SNOW" | "SNOW" | "SHOWER";

export interface WeatherDto {
  /** 이 값이 해당하는 1시간(한국 시간, 정시) */
  forecast_at: string;
  precipitation: Precipitation;
  /** 강수확률(%) */
  probability: number;
  /** 기온(℃). 없으면 null */
  temperature: number | null;
}

/** 격자 하나의 한 발표분: "YYYYMMDDHH00" → 그 시각의 항목들 */
export type Forecast = Map<string, Record<string, string>>;

const ENDPOINT = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
const BASE_HOURS = [2, 5, 8, 11, 14, 17, 20, 23];
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const PUBLISH_DELAY_MS = 10 * 60 * 1000;

/** 기상청 격자 변환(람베르트 정각원추도법, 기상청 동네예보 좌표 변환 기준값) */
export function toGrid(lat: number, lng: number): { nx: number; ny: number } {
  const RE = 6371.00877;
  const GRID = 5.0;
  const SLAT1 = 30.0;
  const SLAT2 = 60.0;
  const OLON = 126.0;
  const OLAT = 38.0;
  const XO = 43;
  const YO = 136;
  const DEGRAD = Math.PI / 180.0;
  const re = RE / GRID;
  const slat1 = SLAT1 * DEGRAD;
  const slat2 = SLAT2 * DEGRAD;
  const olon = OLON * DEGRAD;
  const olat = OLAT * DEGRAD;
  let sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
  sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
  let sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
  sf = (Math.pow(sf, sn) * Math.cos(slat1)) / sn;
  let ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
  ro = (re * sf) / Math.pow(ro, sn);
  let ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5);
  ra = (re * sf) / Math.pow(ra, sn);
  let theta = lng * DEGRAD - olon;
  if (theta > Math.PI) theta -= 2.0 * Math.PI;
  if (theta < -Math.PI) theta += 2.0 * Math.PI;
  theta *= sn;
  return {
    nx: Math.floor(ra * Math.sin(theta) + XO + 0.5),
    ny: Math.floor(ro - ra * Math.cos(theta) + YO + 0.5),
  };
}

/** 지금 조회할 수 있는 가장 최근 발표 시각(한국 시간 날짜 "YYYYMMDD", 시각 "HH00") */
export function latestBase(now: number): { baseDate: string; baseTime: string } {
  const kst = new Date(now + KST_OFFSET_MS - PUBLISH_DELAY_MS);
  const hour = kst.getUTCHours();
  const baseHour = [...BASE_HOURS].reverse().find((h) => h <= hour);
  if (baseHour === undefined) {
    // 02:10 전에는 전날 23시 발표분
    const prev = new Date(kst.getTime() - 24 * 60 * 60 * 1000);
    return { baseDate: ymd(prev), baseTime: "2300" };
  }
  return { baseDate: ymd(kst), baseTime: `${String(baseHour).padStart(2, "0")}00` };
}

export async function fetchForecast(key: string, grid: { nx: number; ny: number }, base: { baseDate: string; baseTime: string }, fetchImpl: typeof fetch = fetch): Promise<Forecast | null> {
  const params = new URLSearchParams({
    serviceKey: key, pageNo: "1", numOfRows: "1000", dataType: "JSON",
    base_date: base.baseDate, base_time: base.baseTime, nx: String(grid.nx), ny: String(grid.ny),
  });
  try {
    const response = await fetchImpl(`${ENDPOINT}?${params}`, { signal: AbortSignal.timeout(5_000) });
    if (!response.ok) {
      console.error(`[weather] HTTP ${response.status}`);
      return null;
    }
    const forecast = parseForecast(await response.json());
    if (!forecast) console.error("[weather] 예보 응답을 읽지 못함");
    return forecast;
  } catch (e) {
    // 키 오류면 JSON 대신 XML 이 와서 여기로 온다. 주소에 키가 있으므로 남기지 않는다.
    console.error(`[weather] ${(e as Error).name}`);
    return null;
  }
}

export function parseForecast(body: unknown): Forecast | null {
  const response = (body as { response?: { header?: { resultCode?: string }; body?: { items?: { item?: unknown } } } }).response;
  if (!response || response.header?.resultCode !== "00") return null;
  const items = response.body?.items?.item;
  if (!Array.isArray(items)) return null;
  const forecast: Forecast = new Map();
  for (const item of items as Array<Record<string, unknown>>) {
    const slot = `${item.fcstDate}${item.fcstTime}`;
    const entry = forecast.get(slot) ?? {};
    entry[String(item.category)] = String(item.fcstValue);
    forecast.set(slot, entry);
  }
  return forecast;
}

/** at 이 속한 1시간의 날씨. 예보 범위(약 3일) 밖이면 null. 발표 직후라 그 시각이 아직 없으면 가장 가까운 다음 시각 */
export function weatherAt(forecast: Forecast, at: number): WeatherDto | null {
  const kst = new Date(at + KST_OFFSET_MS);
  const slot = `${ymd(kst)}${String(kst.getUTCHours()).padStart(2, "0")}00`;
  const slots = [...forecast.keys()].sort();
  const chosen = forecast.has(slot) ? slot : slots.find((s) => s > slot && slotTime(s) - slotTime(slot) <= 3 * 3_600_000);
  if (!chosen) return null;
  const values = forecast.get(chosen)!;
  const temperature = Number(values.TMP);
  return {
    forecast_at: `${chosen.slice(0, 4)}-${chosen.slice(4, 6)}-${chosen.slice(6, 8)}T${chosen.slice(8, 10)}:00:00+09:00`,
    precipitation: PRECIPITATION[values.PTY ?? "0"] ?? "NONE",
    probability: Number(values.POP) || 0,
    temperature: values.TMP !== undefined && Number.isFinite(temperature) ? temperature : null,
  };
}

/** 기상청 강수형태(PTY): 0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기 */
const PRECIPITATION: Record<string, Precipitation> = { "0": "NONE", "1": "RAIN", "2": "RAIN_SNOW", "3": "SNOW", "4": "SHOWER" };

function slotTime(slot: string): number {
  return Date.parse(`${slot.slice(0, 4)}-${slot.slice(4, 6)}-${slot.slice(6, 8)}T${slot.slice(8, 10)}:00:00+09:00`);
}

function ymd(d: Date): string {
  return `${d.getUTCFullYear()}${String(d.getUTCMonth() + 1).padStart(2, "0")}${String(d.getUTCDate()).padStart(2, "0")}`;
}
