package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.InvalidInputReason
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.TransitRoute
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class PlanInput(
    val targetArrival: Instant,
    /** API 가 알려준 현재 이동시간(도보+탑승+환승+대기, 지연 포함) */
    val currentTravel: Duration,
    val delay: Duration = Duration.ZERO,
    val recentTravel: List<Duration> = emptyList(),
    val preparation: Duration,
    val baseBufferMinutes: Int,
    val isRealtime: Boolean,
    val fetchedAt: Instant,
    val route: TransitRoute? = null,
    val previousTravelMinutes: Int? = null,
)

sealed interface PlanOutcome {
    val plan: AlarmPlan

    /**
     * 목표시간 내 도착 가능.
     * @property wakeUpAlreadyPassed 기상 시각은 이미 지났지만 지금 출발하면 늦지 않는 경우
     */
    data class OnTime(override val plan: AlarmPlan, val wakeUpAlreadyPassed: Boolean) : PlanOutcome

    /**
     * 지금 바로 출발해도 목표시간까지 도착할 수 없는 경우.
     * plan 은 "지금 출발" 기준으로 채워져 있다.
     */
    data class TooLate(
        override val plan: AlarmPlan,
        val earliestArrival: Instant,
        val lateByMinutes: Long,
    ) : PlanOutcome
}

/**
 * 시간 계산 파이프라인을 조립한다.
 *
 *   travel     = EstimateTravelTime(현재 ETA, 최근 이동시간)
 *   buffer     = CalculateSafetyBuffer(기본 여유, 지연, 실시간 여부)
 *   departure  = target - travel - buffer
 *   wakeUp     = departure - preparation
 *
 * 각 단계는 독립 UseCase 라서 개별 테스트가 가능하고, 이 클래스는 순서와 예외 상황(과거/지각)만 책임진다.
 * Android 의존성이 없는 순수 Kotlin 이므로 JVM 단위 테스트로 모든 경계 조건을 검증한다.
 */
class BuildAlarmPlanUseCase @Inject constructor(
    private val estimateTravelTime: EstimateTravelTimeUseCase,
    private val calculateSafetyBuffer: CalculateSafetyBufferUseCase,
    private val calculateDepartureTime: CalculateDepartureTimeUseCase,
    private val calculateWakeUpTime: CalculateWakeUpTimeUseCase,
) {

    operator fun invoke(input: PlanInput, now: Instant): AppResult<PlanOutcome> {
        if (!input.targetArrival.isAfter(now)) {
            return AppResult.Failure(AppError.InvalidInput(InvalidInputReason.TARGET_IN_PAST))
        }
        if (input.currentTravel.isNegative || input.preparation.isNegative || input.baseBufferMinutes < 0) {
            return AppResult.Failure(AppError.InvalidInput(InvalidInputReason.NEGATIVE_DURATION))
        }

        val travel = estimateTravelTime(input.currentTravel, input.recentTravel)
        val buffer = calculateSafetyBuffer(input.baseBufferMinutes, input.delay, input.isRealtime)

        val departureTimes = when (val result = calculateDepartureTime(input.targetArrival, travel, buffer)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val wakeUp = when (val result = calculateWakeUpTime(departureTimes.recommendedDeparture, input.preparation)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }

        val basePlan = AlarmPlan(
            targetArrival = input.targetArrival,
            estimatedArrival = departureTimes.estimatedArrival,
            departure = departureTimes.recommendedDeparture,
            wakeUp = wakeUp,
            travelMinutes = travel.toMinutes().toInt(),
            safetyBufferMinutes = buffer.toMinutes().toInt(),
            delayMinutes = input.delay.toMinutes().coerceAtLeast(0).toInt(),
            previousTravelMinutes = input.previousTravelMinutes,
            isRealtime = input.isRealtime,
            lastUpdatedAt = input.fetchedAt,
            route = input.route,
        )

        if (!departureTimes.recommendedDeparture.isBefore(now)) {
            return AppResult.Success(PlanOutcome.OnTime(basePlan, wakeUpAlreadyPassed = wakeUp.isBefore(now)))
        }

        // 권장 출발 시각이 이미 지났다: 지금 출발하는 경우를 기준으로 다시 본다.
        val earliestArrival = now.plus(travel)
        val nowPlan = basePlan.copy(departure = now, wakeUp = now, estimatedArrival = earliestArrival)
        return if (earliestArrival.isAfter(input.targetArrival)) {
            val lateBy = Duration.between(input.targetArrival, earliestArrival).toMinutes()
            AppResult.Success(PlanOutcome.TooLate(nowPlan, earliestArrival, lateBy))
        } else {
            // 여유시간은 줄었지만 지금 출발하면 도착은 가능
            AppResult.Success(PlanOutcome.OnTime(nowPlan, wakeUpAlreadyPassed = true))
        }
    }
}
