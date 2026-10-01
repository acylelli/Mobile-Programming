package com.example.routealarm.data.mapper

import com.example.routealarm.domain.model.AdjustmentKind
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.PendingAdjustment
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.domain.model.TransitSegment
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/*
 * Room 에 JSON 문자열로 저장하는 값들의 직렬화 모델.
 * Domain 모델에 @Serializable 을 붙이지 않는 이유: 저장 포맷이 Domain 변경에 끌려다니지 않게 하고,
 * Domain 이 직렬화 라이브러리에 의존하지 않도록 하기 위함이다.
 */

@Serializable
data class StoredSegment(
    val type: String,
    val lineName: String? = null,
    val startName: String,
    val endName: String,
    val durationSeconds: Long,
    val arrivalInfo: String? = null,
)

@Serializable
data class StoredRoute(
    val totalSeconds: Long,
    val walkingSeconds: Long,
    val waitingSeconds: Long,
    val transferSeconds: Long,
    val departureEpochMillis: Long,
    val arrivalEpochMillis: Long,
    val transferCount: Int,
    val delaySeconds: Long,
    val segments: List<StoredSegment>,
)

@Serializable
data class StoredPlan(
    val targetArrival: Long,
    val estimatedArrival: Long,
    val departure: Long,
    val wakeUp: Long,
    val travelMinutes: Int,
    val safetyBufferMinutes: Int,
    val delayMinutes: Int,
    val previousTravelMinutes: Int? = null,
    val isRealtime: Boolean,
    val lastUpdatedAt: Long,
    val route: StoredRoute? = null,
)

@Serializable
data class StoredPendingAdjustment(
    val kind: String,
    val deltaMinutes: Long,
    val createdAt: Long,
    val plan: StoredPlan,
)

fun TransitRoute.toStored() = StoredRoute(
    totalSeconds = totalDuration.seconds,
    walkingSeconds = walkingDuration.seconds,
    waitingSeconds = waitingDuration.seconds,
    transferSeconds = transferDuration.seconds,
    departureEpochMillis = departureTime.toEpochMilli(),
    arrivalEpochMillis = arrivalTime.toEpochMilli(),
    transferCount = transferCount,
    delaySeconds = delay.seconds,
    segments = segments.map {
        StoredSegment(it.type.name, it.lineName, it.startName, it.endName, it.duration.seconds, it.arrivalInfo)
    },
)

fun StoredRoute.toDomain() = TransitRoute(
    totalDuration = Duration.ofSeconds(totalSeconds),
    walkingDuration = Duration.ofSeconds(walkingSeconds),
    waitingDuration = Duration.ofSeconds(waitingSeconds),
    transferDuration = Duration.ofSeconds(transferSeconds),
    departureTime = Instant.ofEpochMilli(departureEpochMillis),
    arrivalTime = Instant.ofEpochMilli(arrivalEpochMillis),
    transferCount = transferCount,
    delay = Duration.ofSeconds(delaySeconds),
    segments = segments.map {
        TransitSegment(
            type = enumValueOrDefault(it.type, SegmentType.ETC),
            lineName = it.lineName,
            startName = it.startName,
            endName = it.endName,
            duration = Duration.ofSeconds(it.durationSeconds),
            arrivalInfo = it.arrivalInfo,
        )
    },
)

fun AlarmPlan.toStored() = StoredPlan(
    targetArrival = targetArrival.toEpochMilli(),
    estimatedArrival = estimatedArrival.toEpochMilli(),
    departure = departure.toEpochMilli(),
    wakeUp = wakeUp.toEpochMilli(),
    travelMinutes = travelMinutes,
    safetyBufferMinutes = safetyBufferMinutes,
    delayMinutes = delayMinutes,
    previousTravelMinutes = previousTravelMinutes,
    isRealtime = isRealtime,
    lastUpdatedAt = lastUpdatedAt.toEpochMilli(),
    route = route?.toStored(),
)

fun StoredPlan.toDomain() = AlarmPlan(
    targetArrival = Instant.ofEpochMilli(targetArrival),
    estimatedArrival = Instant.ofEpochMilli(estimatedArrival),
    departure = Instant.ofEpochMilli(departure),
    wakeUp = Instant.ofEpochMilli(wakeUp),
    travelMinutes = travelMinutes,
    safetyBufferMinutes = safetyBufferMinutes,
    delayMinutes = delayMinutes,
    previousTravelMinutes = previousTravelMinutes,
    isRealtime = isRealtime,
    lastUpdatedAt = Instant.ofEpochMilli(lastUpdatedAt),
    route = route?.toDomain(),
)

fun PendingAdjustment.toStored() = StoredPendingAdjustment(
    kind = kind.name,
    deltaMinutes = deltaMinutes,
    createdAt = createdAt.toEpochMilli(),
    plan = proposedPlan.toStored(),
)

fun StoredPendingAdjustment.toDomain() = PendingAdjustment(
    kind = enumValueOrDefault(kind, AdjustmentKind.NEEDS_CONFIRMATION),
    proposedPlan = plan.toDomain(),
    deltaMinutes = deltaMinutes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

/** 저장된 enum 이름이 앱 업데이트로 사라져도 크래시하지 않도록 기본값으로 대체 */
inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
