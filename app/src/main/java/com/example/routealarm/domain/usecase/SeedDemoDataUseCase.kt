package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.repository.DemoDataProvider
import com.example.routealarm.domain.repository.PlaceRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

/**
 * 처음 설치한 사용자가 빈 화면만 보지 않도록 예시 일정(집 → 한성대학교, 평일 09:00)을 만든다.
 *
 * 예시 일정은 isDemo = true, enabled = false 로 저장한다. 사용자가 켜기 전에는 실제 알람이 울리지 않고,
 * 화면에는 "예시" 배지로 실제 데이터와 구분된다.
 */
class SeedDemoDataUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val placeRepository: PlaceRepository,
    private val scheduleRepository: ScheduleRepository,
    private val demoDataProvider: DemoDataProvider,
    private val previewSchedule: PreviewScheduleUseCase,
    private val saveSchedule: SaveScheduleUseCase,
) {
    suspend operator fun invoke() {
        if (preferencesRepository.current().demoSeeded) return

        val origin = demoDataProvider.demoOrigin()
        val destination = demoDataProvider.demoDestination()
        val originId = (placeRepository.savePlace(origin) as? AppResult.Success)?.data ?: 0
        val destinationId = (placeRepository.savePlace(destination) as? AppResult.Success)?.data ?: 0

        val draft = ScheduleDraft(
            title = demoDataProvider.demoTitle(),
            origin = origin.copy(id = originId),
            destination = destination.copy(id = destinationId),
            targetArrivalTime = DEMO_ARRIVAL,
            repeatDays = WEEKDAYS,
            preparationMinutes = DEMO_PREPARATION_MINUTES,
            bufferMinutes = DEMO_BUFFER_MINUTES,
            alarmMode = AlarmMode.WAKE_UP_AND_DEPARTURE,
        )
        val preview = previewSchedule(draft)
        if (preview is AppResult.Success) {
            scheduleRepository.deleteDemoSchedules()
            saveSchedule(preview.data, isDemo = true, enabled = false)
        }
        preferencesRepository.setDemoSeeded()
    }

    private companion object {
        val DEMO_ARRIVAL: LocalTime = LocalTime.of(9, 0)
        val WEEKDAYS = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        const val DEMO_PREPARATION_MINUTES = 30
        const val DEMO_BUFFER_MINUTES = 10
    }
}
