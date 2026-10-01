package com.example.routealarm.domain.usecase

import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.policy.RefreshCheckpointPolicy
import com.example.routealarm.domain.repository.AlarmScheduler
import com.example.routealarm.domain.repository.TransitRefreshScheduler
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class AlarmRegistration(
    val registeredTypes: List<AlarmType>,
    /** false 면 정확한 알람 권한이 없어 근사 알람으로 등록됨 → UI 에서 권한 안내 */
    val exact: Boolean,
) {
    companion object {
        val NotScheduled = AlarmRegistration(emptyList(), exact = true)
    }
}

/**
 * 일정의 최신 계산 결과(plan)를 실제 시스템 알람으로 등록한다. 항상 "기존 등록 취소 → 다시 등록" 으로
 * 동작하므로 몇 번을 호출해도 결과가 같다(멱등). 재부팅/시간대 변경/재계산 어디서든 안심하고 부를 수 있다.
 *
 * 기상 알람과 출발 알림을 분리한다:
 *  - WAKE_UP            : 실제 알람 화면 + 알람음 (AlarmManager.setAlarmClock)
 *  - DEPARTURE_REMINDER : 출발 10분 전 "출발 준비를 마쳐주세요" 알림
 *  - DEPARTURE          : 출발 시각 "지금 출발하면 08:52 도착" 알림
 */
class ScheduleAlarmUseCase @Inject constructor(
    private val alarmScheduler: AlarmScheduler,
    private val refreshScheduler: TransitRefreshScheduler,
    private val timeProvider: TimeProvider,
) {

    operator fun invoke(schedule: Schedule): AlarmRegistration {
        // 사용자가 누른 스누즈는 재계산과 무관하게 유지한다.
        alarmScheduler.cancel(schedule.id, PLANNED_TYPES)

        val plan = schedule.plan
        if (!schedule.enabled || plan == null) {
            refreshScheduler.cancelRefresh(schedule.id)
            return AlarmRegistration.NotScheduled
        }

        val now = timeProvider.now()
        val registered = mutableListOf<AlarmType>()
        var allExact = true

        fun register(type: AlarmType, at: Instant) {
            if (!at.isAfter(now)) return
            allExact = alarmScheduler.schedule(schedule.id, type, at) && allExact
            registered += type
        }

        register(AlarmType.WAKE_UP, plan.wakeUp)
        if (schedule.alarmMode == AlarmMode.WAKE_UP_AND_DEPARTURE) {
            val reminderAt = plan.departure.minus(DEPARTURE_REMINDER_LEAD)
            if (reminderAt.isAfter(plan.wakeUp)) register(AlarmType.DEPARTURE_REMINDER, reminderAt)
            register(AlarmType.DEPARTURE, plan.departure)
        }

        val nextRefresh = RefreshCheckpointPolicy.nextCheckpoint(plan.wakeUp, now)
        if (nextRefresh != null) {
            refreshScheduler.scheduleRefresh(schedule.id, nextRefresh)
        } else {
            refreshScheduler.cancelRefresh(schedule.id)
        }
        return AlarmRegistration(registered, allExact)
    }

    companion object {
        val DEPARTURE_REMINDER_LEAD: Duration = Duration.ofMinutes(10)
        val PLANNED_TYPES = listOf(AlarmType.WAKE_UP, AlarmType.DEPARTURE_REMINDER, AlarmType.DEPARTURE)
    }
}
