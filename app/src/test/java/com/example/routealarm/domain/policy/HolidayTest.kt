package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.usecase.ResolveNextOccurrenceUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class HolidayTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val resolve = ResolveNextOccurrenceUseCase()
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val nine = LocalTime.of(9, 0)

    private fun at(date: LocalDate, time: LocalTime = LocalTime.of(20, 0)): Instant = ZonedDateTime.of(date, time, zone).toInstant()

    private fun next(now: Instant, repeat: Set<DayOfWeek> = weekdays, skip: Boolean = true) =
        resolve(nine, repeat, null, now, zone, Duration.ofMinutes(90), skipHolidays = skip)?.atZone(zone)?.toLocalDate()

    @Test
    fun table_includesSubstituteAndElectionDays() {
        assertTrue(KoreanHolidays.isHoliday(LocalDate.of(2026, 12, 25)))
        assertEquals("대체공휴일(설날)", KoreanHolidays.name(LocalDate.of(2027, 2, 9)))
        assertEquals("지방선거일", KoreanHolidays.name(LocalDate.of(2026, 6, 3)))
        assertFalse(KoreanHolidays.isHoliday(LocalDate.of(2026, 12, 24)))
        assertEquals(2027, KoreanHolidays.lastCoveredYear)
    }

    @Test
    fun weekdayAlarm_skipsChristmas() {
        // 2026-12-24(목) 저녁 → 다음 평일 회차는 25일(금, 성탄절)이 아니라 28일(월)
        assertEquals(LocalDate.of(2026, 12, 28), next(at(LocalDate.of(2026, 12, 24))))
        assertEquals(LocalDate.of(2026, 12, 25), next(at(LocalDate.of(2026, 12, 24)), skip = false))
    }

    @Test
    fun weekdayAlarm_skipsWholeLunarNewYearWithSubstituteDay() {
        // 2027 설 연휴 2/6(토)~2/9(화, 대체공휴일) → 2/5(금) 저녁 다음 회차는 2/10(수)
        assertEquals(LocalDate.of(2027, 2, 10), next(at(LocalDate.of(2027, 2, 5))))
    }

    @Test
    fun weeklyAlarm_onHolidayMovesToNextWeek() {
        // 금요일만 울리는 일정: 2026-12-25(금) 성탄절을 건너뛰고 2027-01-01(금) 신정도 건너뛰어 1/8
        assertEquals(LocalDate.of(2027, 1, 8), next(at(LocalDate.of(2026, 12, 24)), repeat = setOf(DayOfWeek.FRIDAY)))
    }

    @Test
    fun nextSkipped_findsUpcomingHolidayOnRepeatDay() {
        val schedule = schedule(weekdays, skip = true)
        val skipped = HolidaySkipPolicy.nextSkipped(schedule, at(LocalDate.of(2026, 12, 20)), zone)
        assertEquals(SkippedHoliday(LocalDate.of(2026, 12, 25), "성탄절"), skipped)
    }

    @Test
    fun nextSkipped_nothingWhenTurnedOffOrNotOnRepeatDay() {
        assertNull(HolidaySkipPolicy.nextSkipped(schedule(weekdays, skip = false), at(LocalDate.of(2026, 12, 20)), zone))
        // 주말만 울리는 일정은 12/25(금)와 겹치지 않는다.
        assertNull(HolidaySkipPolicy.nextSkipped(schedule(setOf(DayOfWeek.SATURDAY), skip = true), at(LocalDate.of(2026, 12, 20)), zone))
    }

    private fun schedule(repeat: Set<DayOfWeek>, skip: Boolean): Schedule {
        val place = Place(name = "다산역", address = "경기 남양주시", latitude = 37.62, longitude = 127.15, type = PlaceType.CUSTOM)
        val epoch = Instant.EPOCH
        return Schedule(
            title = "등교", origin = place, destination = place.copy(name = "한성대학교"),
            targetArrivalTime = nine, preparationMinutes = 30, bufferMinutes = 10,
            repeatDays = repeat, skipHolidays = skip, createdAt = epoch, updatedAt = epoch,
        )
    }
}
