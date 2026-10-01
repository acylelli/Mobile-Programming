package com.example.routealarm.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Backend Proxy 응답 계약(docs/proxy-api.md 참고).
 *
 * Proxy 서버가 ODsay / TMAP / 공공데이터 응답을 이 형태로 정규화해 내려준다.
 * 앱은 제공자별 응답 형식을 몰라도 되고, API Key 는 서버 환경변수에만 존재한다.
 */

@Serializable
data class RouteResponseDto(
    val realtime: Boolean = false,
    val routes: List<RouteDto> = emptyList(),
)

@Serializable
data class RouteDto(
    @SerialName("total_minutes") val totalMinutes: Int,
    @SerialName("walk_minutes") val walkMinutes: Int = 0,
    @SerialName("wait_minutes") val waitMinutes: Int = 0,
    @SerialName("transfer_minutes") val transferMinutes: Int = 0,
    @SerialName("transfer_count") val transferCount: Int = 0,
    @SerialName("delay_minutes") val delayMinutes: Int = 0,
    /** ISO-8601 (예: 2026-10-06T07:45:00+09:00) */
    @SerialName("departure_time") val departureTime: String,
    @SerialName("arrival_time") val arrivalTime: String,
    val segments: List<SegmentDto> = emptyList(),
)

@Serializable
data class SegmentDto(
    /** WALK | BUS | SUBWAY | TRAIN | 그 외 */
    val mode: String,
    @SerialName("line_name") val lineName: String? = null,
    val from: String,
    val to: String,
    val minutes: Int,
    @SerialName("arrival_message") val arrivalMessage: String? = null,
)

@Serializable
data class PlaceSearchResponseDto(
    val places: List<PlaceDto> = emptyList(),
)

@Serializable
data class PlaceDto(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)
