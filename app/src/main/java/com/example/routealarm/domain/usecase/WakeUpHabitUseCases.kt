package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.policy.WakeUpHabit
import com.example.routealarm.domain.policy.WakeUpHabitPolicy
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.HabitRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Duration
import javax.inject.Inject

/** 일정의 기상 습관 제안. 제안할 것이 없으면 null */
class ObserveWakeUpHabitUseCase @Inject constructor(
    private val historyRepository: AlarmHistoryRepository,
    private val habitRepository: HabitRepository,
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(scheduleId: Long): Flow<WakeUpHabit?> = combine(
        historyRepository.observeDismissals(scheduleId),
        habitRepository.resetAt(scheduleId),
    ) { dismissals, since -> WakeUpHabitPolicy.analyze(dismissals, timeProvider.now(), since) }
}

/**
 * 기상 습관 제안 적용: 준비 시간을 늘리고 알람을 다시 계산한다.
 * 이후에는 적용한 다음의 기록만 모아, 늘린 뒤에도 늦는지 다시 본다.
 */
class ApplyWakeUpHabitUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val habitRepository: HabitRepository,
    private val scheduleAlarm: ScheduleAlarmUseCase,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(scheduleId: Long, extraMinutes: Int): AppResult<Unit> {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return AppResult.Failure(AppError.NotFound)
        val now = timeProvider.now()
        val preparationMinutes = (schedule.preparationMinutes + extraMinutes).coerceAtMost(MAX_PREPARATION_MINUTES)
        // 출발 시각·이동시간은 그대로 두고 기상 알람만 늘린 준비 시간만큼 앞당긴다.
        // (다시 계산하면 이동시간 추정까지 바뀌어 사용자가 고른 것보다 더 많이 움직일 수 있다)
        val plan = schedule.plan?.let { last ->
            val wakeUp = last.wakeUp.minus(Duration.ofMinutes((preparationMinutes - schedule.preparationMinutes).toLong()))
            if (wakeUp.isAfter(now)) last.copy(wakeUp = wakeUp) else last
        }
        val updated = schedule.copy(preparationMinutes = preparationMinutes, plan = plan, updatedAt = now)
        val saved = scheduleRepository.upsert(updated)
        if (saved is AppResult.Failure) return saved
        habitRepository.reset(scheduleId, now)
        scheduleAlarm(updated)
        return AppResult.Success(Unit)
    }

    private companion object {
        const val MAX_PREPARATION_MINUTES = 180
    }
}

/** 기상 습관 제안 넘기기: 지금까지의 기록으로는 다시 제안하지 않는다. */
class DismissWakeUpHabitUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(scheduleId: Long) = habitRepository.reset(scheduleId, timeProvider.now())
}
