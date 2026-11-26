package com.example.routealarm.domain.policy

import org.junit.Assert.assertEquals
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

class TravelPatternPolicyTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val monday = LocalDate.of(2026, 11, 23)
    private val now = at(monday.plusDays(3), LocalTime.of(22, 0)) // 목요일 밤

    private fun at(date: LocalDate, time: LocalTime): Instant = ZonedDateTime.of(date, time, zone).toInstant()

    private fun obs(date: LocalDate, departure: LocalTime, minutes: Int) =
        TravelObservation(at(date, LocalTime.of(9, 0)), at(date, departure), minutes)

    /** 최근 4주: 월요일 70분, 화~금 55분, 모두 07:50 출발 */
    private val weeks = (0L..3L).flatMap { week ->
        val mon = monday.minusWeeks(week)
        listOf(obs(mon, LocalTime.of(7, 50), 70)) +
            (1L..4L).map { obs(mon.plusDays(it), LocalTime.of(7, 50), 55) }
    }.filter { !it.departure.isAfter(now) }

    @Test
    fun slotSamples_usesSameWeekdayWithinAnHour() {
        val nextMonday = at(monday.plusWeeks(1), LocalTime.of(8, 30))
        val samples = TravelPatternPolicy.slotSamples(weeks, nextMonday, now, zone)
        assertEquals(List(4) { Duration.ofMinutes(70) }, samples)
    }

    @Test
    fun slotSamples_ignoresOtherTimeOfDayAndTooFewSamples() {
        // 같은 월요일이라도 저녁 출발은 다른 시간대
        assertTrue(TravelPatternPolicy.slotSamples(weeks, at(monday.plusWeeks(1), LocalTime.of(18, 0)), now, zone).isEmpty())
        // 기록이 2번뿐이면 쓰지 않는다
        assertTrue(TravelPatternPolicy.slotSamples(weeks.take(7), at(monday.plusWeeks(1), LocalTime.of(7, 50)), now, zone).isEmpty())
    }

    @Test
    fun slotSamples_dropsRecordsOlderThanEightWeeks() {
        val old = (9L..11L).map { obs(monday.minusWeeks(it), LocalTime.of(7, 50), 90) }
        assertTrue(TravelPatternPolicy.slotSamples(old, at(monday.plusWeeks(1), LocalTime.of(7, 50)), now, zone).isEmpty())
    }

    @Test
    fun pattern_findsSlowWeekday() {
        val pattern = TravelPatternPolicy.pattern(weeks, now, zone)!!
        assertEquals(55, pattern.baselineMinutes)
        val mon = pattern.weekdays.first { it.day == DayOfWeek.MONDAY }
        assertEquals(70, mon.typicalMinutes)
        assertEquals(15, mon.extraMinutes)
        assertEquals(DayOfWeek.MONDAY, pattern.slowest!!.day)
        // 이번 주 금요일은 아직 오지 않아 금요일 기록은 3번
        assertEquals(3, pattern.weekdays.first { it.day == DayOfWeek.FRIDAY }.samples)
    }

    @Test
    fun pattern_nullWhenNoWeekdayHasEnoughSamples() {
        assertNull(TravelPatternPolicy.pattern(weeks.take(2), now, zone))
    }
}
