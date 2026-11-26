package com.example.routealarm.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Proxy 서버 응답 형식(server/src/contract.ts, docs/proxy-api.md).
 * 모르는 필드는 무시하므로 서버가 필드를 추가해도 깨지지 않는다.
 */

@Serializable
data class SegmentDto(
    val mode: String,
    @SerialName("line_name") val lineName: String? = null,
    val from: String = "",
    val to: String = "",
    val minutes: Int = 0,
    @SerialName("arrival_message") val arrivalMessage: String? = null,
)

@Serializable
data class RouteDto(
    @SerialName("total_minutes") val totalMinutes: Int,
    @SerialName("walk_minutes") val walkMinutes: Int = 0,
    @SerialName("wait_minutes") val waitMinutes: Int = 0,
    @SerialName("transfer_minutes") val transferMinutes: Int = 0,
    @SerialName("transfer_count") val transferCount: Int = 0,
    @SerialName("delay_minutes") val delayMinutes: Int = 0,
    @SerialName("departure_time") val departureTime: String = "",
    @SerialName("arrival_time") val arrivalTime: String = "",
    val segments: List<SegmentDto> = emptyList(),
)

@Serializable
data class RouteResponseDto(val realtime: Boolean = false, val routes: List<RouteDto> = emptyList())

@Serializable
data class PlaceDto(
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val lines: List<String> = emptyList(),
)

@Serializable
data class PlaceSearchResponseDto(val places: List<PlaceDto> = emptyList())

@Serializable
data class WeatherDto(
    @SerialName("forecast_at") val forecastAt: String = "",
    val precipitation: String = "NONE",
    val probability: Int = 0,
    val temperature: Int? = null,
)
