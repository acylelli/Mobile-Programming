/*
 * 앱과 맞춘 응답 계약(docs/proxy-api.md).
 * 앱 쪽 DTO: app/src/main/java/com/example/routealarm/data/remote/dto/TransitDtos.kt
 * 필드를 바꾸면 앱 DTO 와 문서도 함께 바꾼다. 새 필드를 추가하는 것은 앱이 무시하므로 안전하다.
 */

export type SegmentMode = "WALK" | "BUS" | "SUBWAY" | "TRAIN" | "ETC";

export interface SegmentDto {
  mode: SegmentMode;
  line_name?: string;
  from: string;
  to: string;
  minutes: number;
  arrival_message?: string;
}

export interface RouteDto {
  total_minutes: number;
  walk_minutes: number;
  wait_minutes: number;
  transfer_minutes: number;
  transfer_count: number;
  delay_minutes: number;
  /** ISO-8601, +09:00 */
  departure_time: string;
  arrival_time: string;
  segments: SegmentDto[];
}

export interface RouteResponseDto {
  realtime: boolean;
  routes: RouteDto[];
}

export interface PlaceDto {
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  lines: string[];
}

export interface PlaceSearchResponseDto {
  places: PlaceDto[];
}

/** 외부 API 호출이 실패했을 때. status 는 앱에 돌려줄 HTTP 상태코드다. */
export class UpstreamError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

/** 2026-10-06T07:45:00+09:00 형식. 앱은 OffsetDateTime 으로 읽는다. */
export function toKstIso(date: Date): string {
  return new Date(date.getTime() + KST_OFFSET_MS).toISOString().slice(0, 19) + "+09:00";
}
