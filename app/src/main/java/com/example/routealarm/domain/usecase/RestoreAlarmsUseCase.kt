package com.example.routealarm.domain.usecase

import com.example.routealarm.domain.repository.ScheduleRepository
import javax.inject.Inject

/**
 * 재부팅/앱 업데이트/시간대 변경 후 AlarmManager 등록이 사라졌을 수 있으므로
 * 저장된 활성 일정을 모두 다시 등록한다.
 */
class RestoreAlarmsUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val syncSchedule: SyncScheduleUseCase,
) {
    suspend operator fun invoke(): Int {
        val schedules = scheduleRepository.getEnabledSchedules()
        schedules.forEach { syncSchedule(it) }
        return schedules.size
    }
}
