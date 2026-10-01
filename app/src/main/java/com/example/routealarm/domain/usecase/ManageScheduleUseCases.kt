package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.AlarmScheduler
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.TransitRefreshScheduler
import java.time.Duration
import javax.inject.Inject

/** 알람 ON/OFF. 켤 때는 다음 회차로 맞춘 뒤 실시간 교통 정보를 바로 한 번 확인한다. */
class SetScheduleEnabledUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
    private val refreshScheduler: TransitRefreshScheduler,
    private val resolveNextOccurrence: ResolveNextOccurrenceUseCase,
    private val syncSchedule: SyncScheduleUseCase,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(scheduleId: Long, enabled: Boolean): AppResult<Unit> {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return AppResult.Failure(AppError.NotFound)
        val now = timeProvider.now()
        if (!enabled) {
            val disabled = schedule.copy(enabled = false, pendingAdjustment = null, updatedAt = now)
            alarmScheduler.cancel(scheduleId)
            refreshScheduler.cancelRefresh(scheduleId)
            return scheduleRepository.upsert(disabled).let { result ->
                if (result is AppResult.Failure) result else AppResult.Success(Unit)
            }
        }
        // 이미 지난 1회 일정을 다시 켜면 다음 날짜로 옮긴다.
        val oneTimeDate = if (schedule.isRepeating) {
            null
        } else {
            resolveNextOccurrence.resolveOneTimeDate(schedule.targetArrivalTime, now, timeProvider.zone())
        }
        val enabledSchedule = schedule.copy(enabled = true, oneTimeDate = oneTimeDate, updatedAt = now)
        val result = scheduleRepository.upsert(enabledSchedule)
        if (result is AppResult.Failure) return result
        syncSchedule(enabledSchedule)
        refreshScheduler.scheduleRefresh(scheduleId, now)
        return AppResult.Success(Unit)
    }
}

class DeleteScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
    private val refreshScheduler: TransitRefreshScheduler,
) {
    suspend operator fun invoke(scheduleId: Long): AppResult<Unit> {
        alarmScheduler.cancel(scheduleId)
        refreshScheduler.cancelRefresh(scheduleId)
        return scheduleRepository.delete(scheduleId)
    }
}

/** 자동 조정 OFF 상태에서 추천된 변경안을 사용자가 [변경하기]로 승인한 경우 */
class ApplyPendingAdjustmentUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val historyRepository: AlarmHistoryRepository,
    private val refreshScheduler: TransitRefreshScheduler,
    private val scheduleAlarm: ScheduleAlarmUseCase,
    private val timeProvider: TimeProvider,
) {
    /** @return 적용했으면 true, 변경안이 이미 낡아 다시 계산을 요청했으면 false */
    suspend operator fun invoke(scheduleId: Long): AppResult<Boolean> {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return AppResult.Failure(AppError.NotFound)
        val pending = schedule.pendingAdjustment ?: return AppResult.Success(false)
        val now = timeProvider.now()
        val current = schedule.plan

        val outdated = current == null ||
            pending.proposedPlan.targetArrival != current.targetArrival ||
            Duration.between(pending.createdAt, now) > PENDING_VALIDITY
        if (outdated) {
            scheduleRepository.upsert(schedule.copy(pendingAdjustment = null, updatedAt = now))
            refreshScheduler.scheduleRefresh(scheduleId, now)
            return AppResult.Success(false)
        }

        var plan = pending.proposedPlan.copy(previousTravelMinutes = current?.travelMinutes)
        if (plan.wakeUp.isBefore(now)) plan = plan.copy(wakeUp = now.plusSeconds(IMMEDIATE_SECONDS))
        val updated = schedule.copy(plan = plan, pendingAdjustment = null, updatedAt = now)
        val result = scheduleRepository.upsert(updated)
        if (result is AppResult.Failure) return result
        scheduleAlarm(updated)
        historyRepository.record(
            AlarmHistoryEvent(
                scheduleId = scheduleId,
                type = AlarmEventType.ADJUSTMENT_APPLIED,
                occurredAt = now,
                previousWakeUp = current?.wakeUp,
                newWakeUp = plan.wakeUp,
                travelMinutes = plan.travelMinutes,
                isRealtime = plan.isRealtime,
            ),
        )
        return AppResult.Success(true)
    }

    private companion object {
        /** 이보다 오래된 추천은 교통 상황이 또 바뀌었을 수 있으므로 다시 계산한다. */
        val PENDING_VALIDITY: Duration = Duration.ofHours(3)
        const val IMMEDIATE_SECONDS = 5L
    }
}

class DismissPendingAdjustmentUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(scheduleId: Long) {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return
        scheduleRepository.upsert(schedule.copy(pendingAdjustment = null, updatedAt = timeProvider.now()))
    }
}
