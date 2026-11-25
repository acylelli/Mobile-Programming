import { type PlaceDto, type PlaceSearchResponseDto, UpstreamError } from "./contract.ts";

/*
 * Kakao 로컬 키워드 검색 → 앱 장소 검색 계약 변환.
 *
 * Kakao 는 환승역을 노선마다 따로 준다("잠실역 2호선", "잠실역 8호선").
 * 앱 검색 결과에는 역 하나에 노선 배지를 여러 개 붙이므로 같은 역은 하나로 합치고 노선을 lines 에 모은다.
 */

const KAKAO_ENDPOINT = "https://dapi.kakao.com/v2/local/search/keyword.json";
const PAGE_SIZE = 15;
const SUBWAY_CATEGORY = "SW8";

export interface KakaoDocument {
  place_name: string;
  address_name: string;
  road_address_name?: string;
  category_group_code?: string;
  x: string;
  y: string;
}

export interface KakaoResponse {
  documents?: KakaoDocument[];
}

export async function fetchKakaoPlaces(apiKey: string, query: string, fetchImpl: typeof fetch = fetch): Promise<PlaceSearchResponseDto> {
  const params = new URLSearchParams({ query, size: String(PAGE_SIZE) });
  let response: Response;
  try {
    response = await fetchImpl(`${KAKAO_ENDPOINT}?${params}`, {
      headers: { Authorization: `KakaoAK ${apiKey}` },
      signal: AbortSignal.timeout(5_000),
    });
  } catch (e) {
    throw new UpstreamError(504, `Kakao 호출 실패: ${(e as Error).name}`);
  }
  if (!response.ok) throw new UpstreamError(502, `Kakao HTTP ${response.status}`);
  return toPlaceResponse((await response.json()) as KakaoResponse);
}

export function toPlaceResponse(body: KakaoResponse): PlaceSearchResponseDto {
  const places: PlaceDto[] = [];
  const stations = new Map<string, PlaceDto>();

  for (const doc of body.documents ?? []) {
    const latitude = Number(doc.y);
    const longitude = Number(doc.x);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) continue;
    const address = doc.road_address_name || doc.address_name;

    const station = doc.category_group_code === SUBWAY_CATEGORY ? splitStation(doc.place_name) : null;
    if (station) {
      const existing = stations.get(station.name);
      if (existing) {
        if (station.line && !existing.lines.includes(station.line)) existing.lines.push(station.line);
        continue;
      }
      const place = { name: station.name, address, latitude, longitude, lines: station.line ? [station.line] : [] };
      stations.set(station.name, place);
      places.push(place);
      continue;
    }
    places.push({ name: doc.place_name, address, latitude, longitude, lines: [] });
  }
  return { places };
}

/** "잠실역 2호선" → { name: "잠실역", line: "2호선" }. 노선 표기가 없으면 line 은 null. */
export function splitStation(placeName: string): { name: string; line: string | null } {
  const match = placeName.match(/^(.+?역)\s+(.+)$/);
  if (!match) return { name: placeName, line: null };
  return { name: match[1]!, line: match[2]!.trim() };
}
