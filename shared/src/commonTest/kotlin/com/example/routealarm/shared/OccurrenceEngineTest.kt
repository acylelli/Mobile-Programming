package com.example.routealarm.shared

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OccurrenceEngineTest {
    private val seoul = TimeZone.of("Asia/Seoul")
    private val nine = LocalTime(9, 0)

    @Test
    fun 오늘_회차가_남아_있으면_오늘() {
        val now = Instant.parse("2026-10-05T20:00:00Z") // 화 05:00 KST
        val next = OccurrenceEngine.next(nine, setOf(DayOfWeek.TUESDAY), null, now, seoul, 90.minutes)
        assertEquals(Instant.parse("2026-10-06T00:00:00Z"), next)
    }

    @Test
    fun 오늘_마지막_알림이_지났으면_다음_주() {
        val now = Instant.parse("2026-10-05T23:00:00Z") // 화 08:00 KST
        val next = OccurrenceEngine.next(nine, setOf(DayOfWeek.TUESDAY), null, now, seoul, 90.minutes)
        assertEquals(Instant.parse("2026-10-06T00:00:00Z") + 7.days, next)
    }

    @Test
    fun 공휴일_건너뛰기를_켜면_한글날을_넘긴다() {
        // 2026-10-09(금) 한글날
        val now = Instant.parse("2026-10-07T15:00:00Z") // 10/8 목 00:00 KST
        val skip = OccurrenceEngine.next(nine, setOf(DayOfWeek.FRIDAY), null, now, seoul, 1.hours, skipHolidays = true)
        val ring = OccurrenceEngine.next(nine, setOf(DayOfWeek.FRIDAY), null, now, seoul, 1.hours, skipHolidays = false)
        assertEquals(Instant.parse("2026-10-09T00:00:00Z"), ring)
        assertEquals(Instant.parse("2026-10-16T00:00:00Z"), skip)
    }

    @Test
    fun 일회_일정은_날짜_그대로고_반복도_날짜도_없으면_null() {
        val now = Instant.parse("2026-10-05T20:00:00Z")
        assertEquals(
            Instant.parse("2026-10-09T00:00:00Z"),
            OccurrenceEngine.next(nine, emptySet(), LocalDate(2026, 10, 9), now, seoul, 1.hours),
        )
        assertNull(OccurrenceEngine.next(nine, emptySet(), null, now, seoul, 1.hours))
    }

    @Test
    fun 일회_일정_날짜는_목표가_지났으면_내일() {
        assertEquals(LocalDate(2026, 10, 6), OccurrenceEngine.oneTimeDate(nine, Instant.parse("2026-10-05T20:00:00Z"), seoul))
        assertEquals(LocalDate(2026, 10, 7), OccurrenceEngine.oneTimeDate(nine, Instant.parse("2026-10-06T01:00:00Z"), seoul))
    }

    @Test
    fun 공휴일_표() {
        assertTrue(KoreanHolidays.isHoliday(LocalDate(2026, 9, 25)))
        assertEquals("대체공휴일(개천절)", KoreanHolidays.name(LocalDate(2026, 10, 5)))
        assertFalse(KoreanHolidays.isHoliday(LocalDate(2026, 10, 6)))
        assertEquals(2027, KoreanHolidays.lastCoveredYear)
    }
}
