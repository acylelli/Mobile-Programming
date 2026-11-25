package com.example.routealarm.presentation.common

import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import com.example.routealarm.presentation.widget.NextAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NextAlarmTest {

    private val day = LocalDate.of(2026, 10, 5)
    private val now = seoul(day, 6, 0)

    private fun plan(wakeHour: Int, departHour: Int): AlarmPlan = AlarmPlan(
        targetArrival = seoul(day, 9, 0),
        estimatedArrival = seoul(day, 8, 50),
        departure = seoul(day, departHour, 0),
        wakeUp = seoul(day, wakeHour, 0),
        travelMinutes = 60,
        safetyBufferMinutes = 10,
        delayMinutes = 0,
        isRealtime = true,
        lastUpdatedAt = now,
    )

    @Test
    fun `켜져 있고 출발 전인 일정 중 기상 알람이 가장 이른 것을 고른다`() {
        val later = TestData.schedule().copy(id = 1, plan = plan(wakeHour = 8, departHour = 9))
        val earlier = TestData.schedule().copy(id = 2, plan = plan(wakeHour = 7, departHour = 8))
        val disabled = TestData.schedule(enabled = false).copy(id = 3, plan = plan(wakeHour = 6, departHour = 7))
        val departed = TestData.schedule().copy(id = 4, plan = plan(wakeHour = 4, departHour = 5))

        assertEquals(2L, NextAlarm.pick(listOf(later, earlier, disabled, departed), now)?.id)
    }

    @Test
    fun `보여 줄 알람이 없으면 null`() {
        assertNull(NextAlarm.pick(listOf(TestData.schedule().copy(id = 1, plan = null)), now))
    }
}
