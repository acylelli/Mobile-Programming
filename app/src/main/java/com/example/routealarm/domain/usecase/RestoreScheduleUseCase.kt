package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.ScheduleRepository
import javax.inject.Inject

/**
 * 되돌리기: 바꾸기(또는 지우기) 직전에 떠 둔 일정을 그대로 다시 저장하고 알람을 다시 등록한다.
 * 지운 일정은 같은 id 로 되살아난다(알람 기록은 함께 지워졌으므로 돌아오지 않는다).
 */
class RestoreScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleAlarm: ScheduleAlarmUseCase,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(snapshot: Schedule): AppResult<Unit> {
        val restored = snapshot.copy(updatedAt = timeProvider.now())
        val saved = scheduleRepository.upsert(restored)
        if (saved is AppResult.Failure) return saved
        scheduleAlarm(restored)
        return AppResult.Success(Unit)
    }
}
