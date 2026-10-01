package com.example.routealarm.domain.usecase

import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.AlarmScheduler
import com.example.routealarm.domain.repository.ScheduleRepository
import javax.inject.Inject

/**
 * 알람이 울리거나 사용자가 끄기/다시 울림을 눌렀을 때의 후처리.
 * 회차의 마지막 이벤트가 끝나면 [SyncScheduleUseCase] 로 다음 회차 알람을 등록한다.
 */
class HandleAlarmEventUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val historyRepository: AlarmHistoryRepository,
    private val alarmScheduler: AlarmScheduler,
    private val syncSchedule: SyncScheduleUseCase,
    private val timeProvider: TimeProvider,
) {

    suspend fun onFired(scheduleId: Long, type: AlarmType): Schedule? {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return null
        when (type) {
            AlarmType.WAKE_UP, AlarmType.SNOOZE -> record(schedule, AlarmEventType.FIRED)
            AlarmType.DEPARTURE -> syncSchedule(schedule)
            AlarmType.DEPARTURE_REMINDER -> Unit
        }
        return schedule
    }

    suspend fun onDismissed(scheduleId: Long) {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return
        alarmScheduler.cancel(scheduleId, listOf(AlarmType.SNOOZE))
        record(schedule, AlarmEventType.DISMISSED)
        if (schedule.alarmMode == AlarmMode.WAKE_UP_ONLY) syncSchedule(schedule)
    }

    suspend fun onSnoozed(scheduleId: Long, minutes: Int) {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return
        val at = timeProvider.now().plusSeconds(minutes * SECONDS_PER_MINUTE)
        alarmScheduler.schedule(scheduleId, AlarmType.SNOOZE, at)
        record(schedule, AlarmEventType.SNOOZED)
    }

    private suspend fun record(schedule: Schedule, type: AlarmEventType) {
        historyRepository.record(
            AlarmHistoryEvent(
                scheduleId = schedule.id,
                type = type,
                occurredAt = timeProvider.now(),
                newWakeUp = schedule.plan?.wakeUp,
                travelMinutes = schedule.plan?.travelMinutes,
                isRealtime = schedule.plan?.isRealtime,
            ),
        )
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
