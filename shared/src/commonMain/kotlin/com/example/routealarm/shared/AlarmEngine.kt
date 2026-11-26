package com.example.routealarm.shared

import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** 계산이 성립하지 않는 입력 */
enum class InvalidReason { TARGET_IN_PAST, NEGATIVE_DURATION }

/** 알람 계산 입력. 시간은 모두 절대 시각(Instant) 위에서 계산한다. */
data class EngineInput(
    val targetArrival: Instant,
    /** 교통 API 가 알려준 지금 이동시간(도보+탑승+환승+대기, 지연 포함) */
    val currentTravel: Duration,
    val delay: Duration = Duration.ZERO,
    /** 같은 요일·시간대(없으면 최근) 실제 이동시간. 안정화 입력 */
    val recentTravel: List<Duration> = emptyList(),
    val preparation: Duration,
    val baseBufferMinutes: Int,
    val isRealtime: Boolean,
    /** 날씨 때문에 더하는 여유(분). 비 5, 눈 10 */
    val weatherExtraMinutes: Int = 0,
)

/** 계산된 시각들 */
data class PlanTimes(
    val targetArrival: Instant,
    val estimatedArrival: Instant,
    val departure: Instant,
    val wakeUp: Instant,
    val travelMinutes: Int,
    val safetyBufferMinutes: Int,
)

sealed interface EngineResult {
    data class Invalid(val reason: InvalidReason) : EngineResult

    /** 제때 도착 가능. 기상 시각이 이미 지났어도 지금 출발하면 늦지 않으면 이쪽 */
    data class OnTime(val times: PlanTimes, val wakeUpAlreadyPassed: Boolean) : EngineResult

    /** 지금 바로 출발해도 늦는다. times 는 "지금 출발" 기준 */
    data class TooLate(val times: PlanTimes, val earliestArrival: Instant, val lateByMinutes: Long) : EngineResult
}

/**
 * 알람 계산 엔진(안드로이드·iOS 공용).
 *
 *   travel     = 안정화(현재 ETA, 과거 이동시간)
 *   buffer     = 기본 여유 + 지연 여유 + (실시간 아님) 불확실성 여유 + 날씨 여유
 *   departure  = target - travel - buffer
 *   wakeUp     = departure - preparation
 */
object AlarmEngine {
    /** 지연이 생기면 더 커질 수 있으니 지연의 30% 를 여유로 더한다(최대 10분). */
    const val DELAY_BUFFER_RATIO = 0.3
    const val MAX_DELAY_EXTRA_MINUTES = 10L

    /** 캐시 데이터로 계산할 때 더하는 여유 */
    const val NON_REALTIME_EXTRA_MINUTES = 5L

    /** 이동시간 안정화 가중치: 현재 ETA 0.7, 과거 평균 0.3 */
    const val CURRENT_WEIGHT = 0.7
    const val HISTORY_WEIGHT = 0.3
    const val HISTORY_SAMPLE_SIZE = 5

    /** 이동시간을 전혀 모를 때 쓰는 기본값(분) */
    const val FALLBACK_TRAVEL_MINUTES = 60L

    private const val EPSILON = 1e-6

    /**
     * 계산에 쓸 이동시간. 과거 평균과 섞되 현재 ETA 보다 짧아지지 않게 한다(평균이 짧다고 지연을 무시하면 지각).
     * 분 단위로 올린다: 1분 일찍 깨우는 것이 1분 늦게 깨우는 것보다 낫다.
     */
    fun estimateTravel(current: Duration, recent: List<Duration> = emptyList()): Duration {
        val currentMinutes = current.inWholeMilliseconds.coerceAtLeast(0) / MILLIS_PER_MINUTE
        val valid = recent.filter { it.isPositive() }
        if (valid.isEmpty()) return ceilMinutes(currentMinutes)
        val average = valid.map { it.inWholeMilliseconds / MILLIS_PER_MINUTE }.average()
        val blended = currentMinutes * CURRENT_WEIGHT + average * HISTORY_WEIGHT
        return ceilMinutes(max(currentMinutes, blended))
    }

    /** safetyBuffer = 기본 여유 + 지연의 30%(최대 10분) + (실시간 아님) 5분 */
    fun safetyBuffer(baseBufferMinutes: Int, delay: Duration, isRealtime: Boolean): Duration {
        val base = baseBufferMinutes.coerceAtLeast(0).toLong()
        val delayMinutes = delay.inWholeMinutes.coerceAtLeast(0)
        val delayExtra = ceil(delayMinutes * DELAY_BUFFER_RATIO).toLong().coerceAtMost(MAX_DELAY_EXTRA_MINUTES)
        val uncertainty = if (isRealtime) 0L else NON_REALTIME_EXTRA_MINUTES
        return (base + delayExtra + uncertainty).minutes
    }

    fun plan(input: EngineInput, now: Instant): EngineResult {
        if (input.targetArrival <= now) return EngineResult.Invalid(InvalidReason.TARGET_IN_PAST)
        if (input.currentTravel.isNegative() || input.preparation.isNegative() || input.baseBufferMinutes < 0) {
            return EngineResult.Invalid(InvalidReason.NEGATIVE_DURATION)
        }
        val travel = estimateTravel(input.currentTravel, input.recentTravel)
        val buffer = safetyBuffer(input.baseBufferMinutes, input.delay, input.isRealtime) +
            input.weatherExtraMinutes.coerceAtLeast(0).minutes
        val departure = input.targetArrival - travel - buffer
        val wakeUp = departure - input.preparation
        val times = PlanTimes(
            targetArrival = input.targetArrival,
            estimatedArrival = departure + travel,
            departure = departure,
            wakeUp = wakeUp,
            travelMinutes = travel.inWholeMinutes.toInt(),
            safetyBufferMinutes = buffer.inWholeMinutes.toInt(),
        )
        if (departure >= now) return EngineResult.OnTime(times, wakeUpAlreadyPassed = wakeUp < now)

        // 권장 출발 시각이 이미 지났다: 지금 출발하는 경우로 다시 본다.
        val earliestArrival = now + travel
        val nowTimes = times.copy(departure = now, wakeUp = now, estimatedArrival = earliestArrival)
        return if (earliestArrival > input.targetArrival) {
            EngineResult.TooLate(nowTimes, earliestArrival, (earliestArrival - input.targetArrival).inWholeMinutes)
        } else {
            EngineResult.OnTime(nowTimes, wakeUpAlreadyPassed = true)
        }
    }

    private fun ceilMinutes(minutes: Double): Duration = ceil(minutes - EPSILON).toLong().minutes

    private const val MILLIS_PER_MINUTE = 60_000.0
}
