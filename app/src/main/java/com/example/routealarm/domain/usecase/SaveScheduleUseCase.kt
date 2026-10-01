package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.InvalidInputReason
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import javax.inject.Inject

data class SaveScheduleResult(val scheduleId: Long, val registration: AlarmRegistration)

/**
 * 미리보기 결과를 일정으로 저장하고 알람을 등록한다. editingScheduleId 가 있으면 수정.
 */
class SaveScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val historyRepository: AlarmHistoryRepository,
    private val scheduleAlarm: ScheduleAlarmUseCase,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(
        preview: SchedulePreview,
        editingScheduleId: Long? = null,
        isDemo: Boolean = false,
        enabled: Boolean = true,
    ): AppResult<SaveScheduleResult> {
        if (preview.outcome is PlanOutcome.TooLate && preview.draft.repeatDays.isEmpty()) {
            return AppResult.Failure(AppError.InvalidInput(InvalidInputReason.TARGET_IN_PAST))
        }
        val now = timeProvider.now()
        val existing = editingScheduleId?.let { scheduleRepository.getSchedule(it) }
        val draft = preview.draft

        val schedule = Schedule(
            id = existing?.id ?: 0,
            title = draft.title.ifBlank { preview.destination.name },
            origin = preview.resolvedOrigin,
            destination = preview.destination,
            useCurrentLocation = draft.useCurrentLocation,
            targetArrivalTime = draft.targetArrivalTime,
            preparationMinutes = draft.preparationMinutes,
            bufferMinutes = draft.bufferMinutes,
            repeatDays = draft.repeatDays,
            oneTimeDate = preview.oneTimeDate,
            alarmMode = draft.alarmMode,
            enabled = enabled,
            isDemo = isDemo,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            plan = preview.outcome.plan,
            pendingAdjustment = null,
        )

        val id = when (val result = scheduleRepository.upsert(schedule)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val saved = schedule.copy(id = id)
        historyRepository.record(
            AlarmHistoryEvent(
                scheduleId = id,
                type = AlarmEventType.CREATED,
                occurredAt = now,
                newWakeUp = saved.plan?.wakeUp,
                travelMinutes = saved.plan?.travelMinutes,
                isRealtime = saved.plan?.isRealtime,
            ),
        )
        return AppResult.Success(SaveScheduleResult(id, scheduleAlarm(saved)))
    }
}
