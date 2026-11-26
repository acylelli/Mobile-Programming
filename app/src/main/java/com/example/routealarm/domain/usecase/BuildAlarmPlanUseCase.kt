package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.InvalidInputReason
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.PlanWeather
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.shared.AlarmEngine
import com.example.routealarm.shared.EngineInput
import com.example.routealarm.shared.EngineResult
import com.example.routealarm.shared.InvalidReason
import com.example.routealarm.shared.PlanTimes
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinDuration
import kotlin.time.toKotlinInstant

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
    /** 출발 시각 날씨. 비·눈이면 그만큼 여유를 더한다(WeatherPolicy). */
    val weather: PlanWeather? = null,
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
 * 알람 계산은 안드로이드·iOS 가 함께 쓰는 shared 모듈의 [AlarmEngine] 이 한다.
 *
 *   travel     = 이동시간 안정화(현재 ETA, 최근 이동시간)
 *   buffer     = 기본 여유 + 지연 여유 + (실시간 아님) 불확실성 여유 + 날씨 여유(비 +5, 눈 +10)
 *   departure  = target - travel - buffer
 *   wakeUp     = departure - preparation
 *
 * 이 클래스는 앱의 모델(java.time, AlarmPlan)과 엔진 사이의 변환만 맡는다.
 */
class BuildAlarmPlanUseCase @Inject constructor() {

    operator fun invoke(input: PlanInput, now: Instant): AppResult<PlanOutcome> {
        val result = AlarmEngine.plan(
            EngineInput(
                targetArrival = input.targetArrival.toKotlinInstant(),
                currentTravel = input.currentTravel.toKotlinDuration(),
                delay = input.delay.toKotlinDuration(),
                recentTravel = input.recentTravel.map { it.toKotlinDuration() },
                preparation = input.preparation.toKotlinDuration(),
                baseBufferMinutes = input.baseBufferMinutes,
                isRealtime = input.isRealtime,
                weatherExtraMinutes = input.weather?.extraMinutes ?: 0,
            ),
            now.toKotlinInstant(),
        )
        return when (result) {
            is EngineResult.Invalid -> AppResult.Failure(
                AppError.InvalidInput(
                    when (result.reason) {
                        InvalidReason.TARGET_IN_PAST -> InvalidInputReason.TARGET_IN_PAST
                        InvalidReason.NEGATIVE_DURATION -> InvalidInputReason.NEGATIVE_DURATION
                    },
                ),
            )
            is EngineResult.OnTime -> AppResult.Success(PlanOutcome.OnTime(result.times.toPlan(input), result.wakeUpAlreadyPassed))
            is EngineResult.TooLate -> AppResult.Success(
                PlanOutcome.TooLate(result.times.toPlan(input), result.earliestArrival.toJavaInstant(), result.lateByMinutes),
            )
        }
    }

    private fun PlanTimes.toPlan(input: PlanInput) = AlarmPlan(
        targetArrival = targetArrival.toJavaInstant(),
        estimatedArrival = estimatedArrival.toJavaInstant(),
        departure = departure.toJavaInstant(),
        wakeUp = wakeUp.toJavaInstant(),
        travelMinutes = travelMinutes,
        safetyBufferMinutes = safetyBufferMinutes,
        delayMinutes = input.delay.toMinutes().coerceAtLeast(0).toInt(),
        previousTravelMinutes = input.previousTravelMinutes,
        isRealtime = input.isRealtime,
        lastUpdatedAt = input.fetchedAt,
        route = input.route,
        weather = input.weather,
    )
}
