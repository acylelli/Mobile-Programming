package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AdjustmentKind
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.PendingAdjustment
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.policy.AdjustmentDecision
import com.example.routealarm.domain.policy.AdjustmentPolicy
import com.example.routealarm.domain.policy.TravelEstimatePolicy
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.AlarmNotifier
import com.example.routealarm.domain.repository.LocationRepository
import com.example.routealarm.domain.repository.MetricsRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.TransitRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

sealed interface RefreshOutcome {
    data object NotFound : RefreshOutcome
    data object Disabled : RefreshOutcome

    /** 새 회차를 처음 계산함 */
    data class Recalculated(val schedule: Schedule) : RefreshOutcome

    /** 변화가 작아 알람 유지 (예상 도착 등 정보만 갱신) */
    data class Unchanged(val schedule: Schedule, val deltaMinutes: Long) : RefreshOutcome
    data class AutoAdjusted(val schedule: Schedule, val deltaMinutes: Long) : RefreshOutcome
    data class PendingUserDecision(val schedule: Schedule, val deltaMinutes: Long) : RefreshOutcome

    /** 실시간 정보를 못 가져옴. 마지막 계산 결과로 알람은 유지된다. */
    data class Failed(val error: AppError) : RefreshOutcome
}

/**
 * 교통 정보를 다시 확인하고 필요하면 알람을 조정한다. 앱의 핵심 차별점.
 *
 * 흐름: 다음 회차 결정 → (필요 시) 현재 위치 1회 조회 → 경로 조회(실패 시 캐시) → 새 계획 계산
 *      → [EvaluateAlarmAdjustmentUseCase] 정책으로 유지/자동조정/추천/확인요청 결정 → 저장/알람 등록/알림
 *
 * 사용자가 모르게 알람이 바뀌지 않도록 자동 조정 시 항상 알림을 보내고(설정에서 끌 수 있음),
 * 큰 변화는 자동으로 바꾸지 않고 사용자 확인을 받는다.
 */
class RefreshTransitTimeUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val transitRepository: TransitRepository,
    private val locationRepository: LocationRepository,
    private val historyRepository: AlarmHistoryRepository,
    private val metricsRepository: MetricsRepository,
    private val resolveNextOccurrence: ResolveNextOccurrenceUseCase,
    private val buildAlarmPlan: BuildAlarmPlanUseCase,
    private val evaluateAdjustment: EvaluateAlarmAdjustmentUseCase,
    private val scheduleAlarm: ScheduleAlarmUseCase,
    private val syncSchedule: SyncScheduleUseCase,
    private val notifier: AlarmNotifier,
    private val adjustmentPolicy: AdjustmentPolicy,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(scheduleId: Long): RefreshOutcome {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return RefreshOutcome.NotFound
        if (!schedule.enabled) return RefreshOutcome.Disabled

        val now = timeProvider.now()
        val target = resolveNextOccurrence(
            targetTime = schedule.targetArrivalTime,
            repeatDays = schedule.repeatDays,
            oneTimeDate = schedule.oneTimeDate,
            now = now,
            zone = timeProvider.zone(),
            leadTime = schedule.occurrenceLeadTime(),
        )
        if (target == null || !target.isAfter(now)) {
            syncSchedule(schedule) // 끝난 1회 일정 정리
            return RefreshOutcome.Disabled
        }

        val origin = resolveOrigin(schedule)
        val lastTravel = Duration.ofMinutes(
            schedule.plan?.travelMinutes?.toLong() ?: TravelEstimatePolicy.FALLBACK_TRAVEL_MINUTES,
        )
        val departureGuess = target.minus(lastTravel).minusSeconds(schedule.bufferMinutes * 60L)

        val query = when (val result = transitRepository.getRoutes(origin, schedule.destination, departureGuess)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return handleFailure(schedule, result.error, now)
        }
        val route = query.bestRoute ?: return handleFailure(schedule, AppError.NoRoute, now)

        val recentTravel = historyRepository.recentTravelDurations(
            scheduleId,
            TravelEstimatePolicy.HISTORY_SAMPLE_SIZE,
        )
        val input = PlanInput(
            targetArrival = target,
            currentTravel = route.totalDuration,
            delay = route.delay,
            recentTravel = recentTravel,
            preparation = schedule.preparation,
            baseBufferMinutes = schedule.bufferMinutes,
            isRealtime = query.isRealtime,
            fetchedAt = query.fetchedAt,
            route = route,
            previousTravelMinutes = schedule.plan?.travelMinutes,
        )
        val newPlan = when (val result = buildAlarmPlan(input, now)) {
            is AppResult.Success -> result.data.plan
            is AppResult.Failure -> return handleFailure(schedule, result.error, now)
        }
        metricsRepository.record(MetricType.ALARM_RECALCULATION)

        val oldPlan = schedule.plan
        if (oldPlan == null || oldPlan.targetArrival != newPlan.targetArrival) {
            val saved = save(schedule.copy(plan = newPlan, pendingAdjustment = null, updatedAt = now))
            record(saved, AlarmEventType.RECALCULATED, now, oldPlan?.wakeUp, newPlan)
            return RefreshOutcome.Recalculated(saved)
        }

        val prefs = preferencesRepository.current()
        return when (
            val decision = evaluateAdjustment(
                currentWakeUp = oldPlan.wakeUp,
                proposedWakeUp = newPlan.wakeUp,
                autoAdjustEnabled = prefs.autoAdjustment,
                policy = adjustmentPolicy,
                now = now,
            )
        ) {
            is AdjustmentDecision.Keep -> {
                val saved = save(
                    schedule.copy(plan = keepAlarmWithFreshEstimate(oldPlan, newPlan), pendingAdjustment = null, updatedAt = now),
                )
                record(saved, AlarmEventType.RECALCULATED, now, oldPlan.wakeUp, saved.plan)
                RefreshOutcome.Unchanged(saved, decision.deltaMinutes)
            }

            is AdjustmentDecision.AutoAdjust -> {
                val adjustedPlan = newPlan.withWakeUpNotInPast(oldPlan, now)
                val saved = save(schedule.copy(plan = adjustedPlan, pendingAdjustment = null, updatedAt = now))
                record(saved, AlarmEventType.AUTO_ADJUSTED, now, oldPlan.wakeUp, adjustedPlan)
                metricsRepository.record(MetricType.ALARM_AUTO_ADJUSTED)
                if (prefs.trafficNotificationsEnabled) {
                    notifier.notifyAlarmAdjusted(saved, oldPlan.wakeUp, adjustedPlan.wakeUp)
                }
                RefreshOutcome.AutoAdjusted(saved, decision.deltaMinutes)
            }

            is AdjustmentDecision.Suggest, is AdjustmentDecision.RequireConfirmation -> {
                val kind = if (decision is AdjustmentDecision.Suggest) {
                    AdjustmentKind.SUGGESTED
                } else {
                    AdjustmentKind.NEEDS_CONFIRMATION
                }
                val pending = PendingAdjustment(kind, newPlan, decision.deltaMinutes, now)
                val saved = save(
                    schedule.copy(
                        plan = keepAlarmWithFreshEstimate(oldPlan, newPlan),
                        pendingAdjustment = pending,
                        updatedAt = now,
                    ),
                )
                record(saved, AlarmEventType.ADJUSTMENT_SUGGESTED, now, oldPlan.wakeUp, newPlan)
                if (prefs.trafficNotificationsEnabled) {
                    notifier.notifyAdjustmentSuggested(
                        saved,
                        decision.deltaMinutes,
                        needsConfirmation = kind == AdjustmentKind.NEEDS_CONFIRMATION,
                    )
                }
                RefreshOutcome.PendingUserDecision(saved, decision.deltaMinutes)
            }
        }
    }

    /**
     * 현재 위치 출발 일정이면 이 순간에만 위치를 1회 조회한다.
     * 조회한 좌표는 경로 계산에만 쓰고 저장하지 않는다(위치 이력 최소화).
     */
    private suspend fun resolveOrigin(schedule: Schedule): Place {
        if (!schedule.useCurrentLocation) return schedule.origin
        return when (val location = locationRepository.getCurrentLocation()) {
            is AppResult.Success -> schedule.origin.copy(
                latitude = location.data.latitude,
                longitude = location.data.longitude,
            )
            is AppResult.Failure -> schedule.origin
        }
    }

    private suspend fun handleFailure(schedule: Schedule, error: AppError, now: Instant): RefreshOutcome {
        // 실시간 정보를 못 가져와도 알람은 마지막 계산 결과로 유지한다. UI 에는 "실시간 아님"으로 표시된다.
        val marked = schedule.plan?.let { schedule.copy(plan = it.copy(isRealtime = false)) } ?: schedule
        val saved = save(marked.copy(updatedAt = now))
        syncSchedule(saved)
        historyRepository.record(
            AlarmHistoryEvent(scheduleId = schedule.id, type = AlarmEventType.REFRESH_FAILED, occurredAt = now),
        )
        return RefreshOutcome.Failed(error)
    }

    private suspend fun save(schedule: Schedule): Schedule {
        scheduleRepository.upsert(schedule)
        scheduleAlarm(schedule)
        return schedule
    }

    private suspend fun record(
        schedule: Schedule,
        type: AlarmEventType,
        now: Instant,
        previousWakeUp: Instant?,
        plan: AlarmPlan?,
    ) {
        historyRepository.record(
            AlarmHistoryEvent(
                scheduleId = schedule.id,
                type = type,
                occurredAt = now,
                previousWakeUp = previousWakeUp,
                newWakeUp = plan?.wakeUp,
                travelMinutes = plan?.travelMinutes,
                isRealtime = plan?.isRealtime,
            ),
        )
    }

    /** 알람/출발 시각은 그대로 두고 예상 이동·도착 정보만 최신값으로 바꾼다. */
    private fun keepAlarmWithFreshEstimate(old: AlarmPlan, new: AlarmPlan): AlarmPlan = old.copy(
        estimatedArrival = old.departure.plusSeconds(new.travelMinutes * SECONDS_PER_MINUTE),
        travelMinutes = new.travelMinutes,
        delayMinutes = new.delayMinutes,
        previousTravelMinutes = old.travelMinutes,
        isRealtime = new.isRealtime,
        lastUpdatedAt = new.lastUpdatedAt,
        route = new.route,
    )

    /**
     * 알람 직전 재확인에서 "이미 지난 시각에 일어났어야 함"이 나오면,
     * 아직 울리지 않은 알람을 즉시 울리도록 당긴다(조용히 건너뛰면 지각한다).
     */
    private fun AlarmPlan.withWakeUpNotInPast(old: AlarmPlan, now: Instant): AlarmPlan =
        if (wakeUp.isBefore(now) && old.wakeUp.isAfter(now)) copy(wakeUp = now.plus(IMMEDIATE_ALARM_DELAY)) else this

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        val IMMEDIATE_ALARM_DELAY: Duration = Duration.ofSeconds(5)
    }
}
