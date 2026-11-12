package com.example.routealarm.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * 결제·이용권 검증 서버 응답 계약(INR-003).
 * 앱은 영수증을 직접 판단하지 않고 서버가 검증해 준 결과만 저장한다(SER-001).
 */

@Serializable
data class SubscriptionStatusDto(
    @SerialName("product_id") val productId: String,
    /** ISO-8601 (예: 2026-10-23T00:00:00Z) */
    @SerialName("started_at") val startedAt: String,
    @SerialName("trial_ends_at") val trialEndsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("auto_renew") val autoRenew: Boolean = true,
)

@Serializable
data class GroupPassDto(
    val code: String,
    val organization: String,
    /** ISO-8601 날짜 (예: 2026-03-01) */
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_until") val validUntil: String,
    @SerialName("remaining_seats") val remainingSeats: Int,
)
