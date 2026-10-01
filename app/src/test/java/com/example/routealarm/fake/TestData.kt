package com.example.routealarm.fake

import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.usecase.BuildAlarmPlanUseCase
import com.example.routealarm.domain.usecase.CalculateDepartureTimeUseCase
import com.example.routealarm.domain.usecase.CalculateSafetyBufferUseCase
import com.example.routealarm.domain.usecase.CalculateWakeUpTimeUseCase
import com.example.routealarm.domain.usecase.EstimateTravelTimeUseCase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object TestData {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    val home = Place(1, "집", "경기도 남양주시 다산동", 37.6245, 127.1532, PlaceType.HOME)
    val school = Place(2, "한성대학교", "서울 성북구 삼선교로16길 116", 37.5826, 127.0105, PlaceType.SCHOOL)

    fun seoul(date: LocalDate, hour: Int, minute: Int): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(SEOUL).toInstant()

    fun schedule(
        targetTime: LocalTime = LocalTime.of(9, 0),
        repeatDays: Set<DayOfWeek> = emptySet(),
        oneTimeDate: LocalDate? = null,
        preparationMinutes: Int = 30,
        bufferMinutes: Int = 10,
        alarmMode: AlarmMode = AlarmMode.WAKE_UP_AND_DEPARTURE,
        enabled: Boolean = true,
    ) = Schedule(
        id = 0,
        title = "학교",
        origin = home,
        destination = school,
        targetArrivalTime = targetTime,
        preparationMinutes = preparationMinutes,
        bufferMinutes = bufferMinutes,
        repeatDays = repeatDays,
        oneTimeDate = oneTimeDate,
        alarmMode = alarmMode,
        enabled = enabled,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    fun buildAlarmPlanUseCase() = BuildAlarmPlanUseCase(
        EstimateTravelTimeUseCase(),
        CalculateSafetyBufferUseCase(),
        CalculateDepartureTimeUseCase(),
        CalculateWakeUpTimeUseCase(),
    )
}
