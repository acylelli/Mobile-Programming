package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmStatsPolicyTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 26)
    private val now = at(today, 21, 0)

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant = ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant()

    /** daysAgo 일 전 07:00 알람을 lateMinutes 뒤에 끈 기록 */
    private fun dismissed(daysAgo: Long, lateMinutes: Long, scheduleId: Long = 1) = today.minusDays(daysAgo).let { date ->
        AlarmHistoryEvent(scheduleId = scheduleId, type = AlarmEventType.DISMISSED, occurredAt = at(date, 7, 0).plusSeconds(lateMinutes * 60), newWakeUp = at(date, 7, 0))
    }

    private fun snoozed(daysAgo: Long) = today.minusDays(daysAgo).let { date ->
        AlarmHistoryEvent(scheduleId = 1, type = AlarmEventType.SNOOZED, occurredAt = at(date, 7, 1), newWakeUp = at(date, 7, 0))
    }

    @Test
    fun summarize_countsOnTimeMorningsAverageAndSnoozes() {
        val events = listOf(dismissed(1, 2), dismissed(2, 12), dismissed(3, 0), dismissed(4, 6), snoozed(2), snoozed(2))
        val stats = AlarmStatsPolicy.summarize(events, now, zone)
        assertEquals(4, stats.mornings)
        assertEquals(2, stats.onTimeMornings) // 2분·0분
        assertEquals(50, stats.onTimeRate)
        assertEquals(5, stats.averageLateMinutes) // (2+12+0+6)/4
        assertEquals(2, stats.snoozes)
    }

    @Test
    fun summarize_sumsHowMuchAdjustmentsMovedAlarmsEarlier() {
        val d = today.minusDays(1)
        val events = listOf(
            AlarmHistoryEvent(scheduleId = 1, type = AlarmEventType.AUTO_ADJUSTED, occurredAt = at(d, 5, 0), previousWakeUp = at(d, 7, 0), newWakeUp = at(d, 6, 45)),
            AlarmHistoryEvent(scheduleId = 2, type = AlarmEventType.ADJUSTMENT_APPLIED, occurredAt = at(d, 5, 30), previousWakeUp = at(d, 8, 0), newWakeUp = at(d, 7, 52)),
            // 늦춘 조정은 앞당긴 시간에 넣지 않는다
            AlarmHistoryEvent(scheduleId = 1, type = AlarmEventType.AUTO_ADJUSTED, occurredAt = at(d, 6, 0), previousWakeUp = at(d, 6, 45), newWakeUp = at(d, 6, 55)),
        )
        val stats = AlarmStatsPolicy.summarize(events, now, zone)
        assertEquals(3, stats.adjustments)
        assertEquals(23, stats.adjustedEarlierMinutes)
    }

    @Test
    fun summarize_dropsOldAndAbnormalRecords() {
        val events = listOf(dismissed(31, 3), dismissed(1, 120), dismissed(1, 4))
        val stats = AlarmStatsPolicy.summarize(events, now, zone)
        assertEquals(1, stats.mornings) // 30일 지난 기록과 90분 넘는 값은 뺀다
        assertNull(AlarmStatsPolicy.summarize(emptyList(), now, zone).onTimeRate)
    }

    @Test
    fun days_coverLastTwoWeeksWithLatestMorningPerDay() {
        val events = listOf(dismissed(0, 3, scheduleId = 1), dismissed(0, 9, scheduleId = 2), dismissed(5, 0), snoozed(5))
        val days = AlarmStatsPolicy.summarize(events, now, zone).days
        assertEquals(14, days.size)
        assertEquals(today.minusDays(13), days.first().date)
        assertEquals(DayWake(today, 9, 0), days.last())
        assertEquals(DayWake(today.minusDays(5), 0, 1), days[8])
        assertNull(days[0].lateMinutes)
    }
}
