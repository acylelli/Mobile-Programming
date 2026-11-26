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
    /** 탑승 구간이 지나는 정류장·역 좌표 [위도, 경도]. 도보 구간에는 없다. */
    val points: List<List<Double>> = emptyList(),
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

@Serializable
data class TaxiEstimateDto(
    val minutes: Int,
    @SerialName("distance_meters") val distanceMeters: Int = 0,
    @SerialName("fare_won") val fareWon: Int = 0,
    @SerialName("toll_won") val tollWon: Int = 0,
)

/** 결제 전에 서버에 만드는 주문(POST /v1/payments/orders). iOS 는 platform="ios" 로 앱 링크를 받는다. */
@Serializable
data class CreateOrderRequestDto(
    @SerialName("product_id") val productId: String,
    val platform: String? = null,
)

@Serializable
data class CreateOrderResponseDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("checkout_url") val checkoutUrl: String,
)

@Serializable
data class SubscriptionStatusDto(
    @SerialName("product_id") val productId: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("trial_ends_at") val trialEndsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("auto_renew") val autoRenew: Boolean = true,
)

/** 서버가 승인한 주문 결과(GET /v1/payments/{orderId}). status: READY | PAID | FAILED */
@Serializable
data class PaymentResultDto(
    @SerialName("order_id") val orderId: String,
    val status: String,
    @SerialName("fail_code") val failCode: String? = null,
    val subscription: SubscriptionStatusDto? = null,
)

@Serializable
data class GroupPassDto(
    val code: String,
    val organization: String,
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_until") val validUntil: String,
    @SerialName("remaining_seats") val remainingSeats: Int,
)
