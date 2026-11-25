import { UpstreamError } from "./contract.ts";

/**
 * 택시 예상 시간·요금(카카오모빌리티 자동차 길찾기). 앱의 "지각 위험 → 택시 안내"에 쓴다.
 * 장소 검색과 같은 카카오 REST 키를 쓰며, 지금 출발 기준의 실시간 교통이 반영된 값이다.
 */
const DIRECTIONS_ENDPOINT = "https://apis-navi.kakaomobility.com/v1/directions";

export interface TaxiQuery {
  originLat: number;
  originLng: number;
  destinationLat: number;
  destinationLng: number;
}

export interface TaxiEstimateDto {
  /** 예상 소요 시간(분, 올림) */
  minutes: number;
  distance_meters: number;
  /** 예상 택시 요금(원). 통행료 별도 */
  fare_won: number;
  toll_won: number;
}

interface DirectionsResponse {
  routes?: Array<{
    result_code?: number;
    summary?: { duration?: number; distance?: number; fare?: { taxi?: number; toll?: number } };
  }>;
}

export async function fetchTaxiEstimate(apiKey: string, query: TaxiQuery, fetchImpl: typeof fetch = fetch): Promise<TaxiEstimateDto | null> {
  const params = new URLSearchParams({
    // 카카오모빌리티는 "경도,위도" 순서다.
    origin: `${query.originLng},${query.originLat}`,
    destination: `${query.destinationLng},${query.destinationLat}`,
    summary: "true",
  });
  let response: Response;
  try {
    response = await fetchImpl(`${DIRECTIONS_ENDPOINT}?${params}`, {
      headers: { Authorization: `KakaoAK ${apiKey}` },
      signal: AbortSignal.timeout(6_000),
    });
  } catch (e) {
    throw new UpstreamError(504, `카카오모빌리티 호출 실패: ${(e as Error).name}`);
  }
  if (!response.ok) throw new UpstreamError(502, `카카오모빌리티 HTTP ${response.status}`);
  return toTaxiEstimate((await response.json()) as DirectionsResponse);
}

/** 길을 찾지 못하면(출발·도착이 너무 가깝거나 도로가 없음) null */
export function toTaxiEstimate(body: DirectionsResponse): TaxiEstimateDto | null {
  const route = body.routes?.[0];
  const summary = route?.summary;
  if (!route || route.result_code !== 0 || !summary || summary.duration === undefined) return null;
  return {
    minutes: Math.max(1, Math.ceil(summary.duration / 60)),
    distance_meters: Math.round(summary.distance ?? 0),
    fare_won: Math.round(summary.fare?.taxi ?? 0),
    toll_won: Math.round(summary.fare?.toll ?? 0),
  };
}
