package com.example.routealarm.shared

import com.example.routealarm.shared.history.AlarmEvent
import com.example.routealarm.shared.history.AlarmHistoryRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AlarmHistoryRulesTest {
    private val minute = 60_000L
    private val day = 24 * 60 * minute
    private val now = 1_800_000_000_000L

    private fun dismissed(daysAgo: Int, late: Int) = AlarmEvent("DISMISSED", now - daysAgo * day + late * minute, newWakeUpMillis = now - daysAgo * day)

    @Test
    fun 통계는_제시간_기상률과_앞당긴_조정만_합친다() {
        val events = listOf(
            dismissed(1, 2), dismissed(2, 12), dismissed(3, 4), dismissed(4, -1), dismissed(5, 120),
            AlarmEvent("SNOOZED", now - day),
            AlarmEvent("AUTO_ADJUSTED", now - day, newWakeUpMillis = now - 10 * minute, previousWakeUpMillis = now),
            AlarmEvent("AUTO_ADJUSTED", now - day, newWakeUpMillis = now + 10 * minute, previousWakeUpMillis = now),
        )
        val stats = AlarmHistoryRules.stats(events, now)
        assertEquals(3, stats.mornings)
        assertEquals(67, stats.onTimeRate)
        assertEquals(6, stats.averageLateMinutes)
        assertEquals(2, stats.adjustments)
        assertEquals(10, stats.adjustedEarlierMinutes)
        assertEquals(14, stats.days.size)
    }

    @Test
    fun 습관은_중앙값을_5분_단위로_올려_제안() {
        val habit = assertNotNull(AlarmHistoryRules.habit(listOf(dismissed(1, 7), dismissed(2, 8), dismissed(3, 40)), now, 0))
        assertEquals(8, habit.typicalLateMinutes)
        assertEquals(10, habit.extraPreparationMinutes)
        assertNull(AlarmHistoryRules.habit(listOf(dismissed(1, 7), dismissed(2, 8)), now, 0))
    }

    @Test
    fun 출발_전_교통이_나빠지면_지각_위험() {
        val target = now + 60 * minute
        val risk = assertNotNull(AlarmHistoryRules.lateRisk(target, now + 10 * minute, target + 7 * minute, 50, 0, 0, 30, null, now))
        assertEquals("TRAFFIC", risk.reason)
        assertEquals(7, risk.lateMinutes)
        assertNull(AlarmHistoryRules.lateRisk(target + 5 * 60 * minute, now, target + 6 * 60 * minute, 50, 0, 0, 30, null, now))
    }
}
