package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WakeUpHabitPolicyTest {

    private val now = Instant.parse("2026-11-26T00:00:00Z")

    /** [daysAgo]일 전 07:00 예정 기상 알람을 [lateMinutes]분 뒤에 껐다 */
    private fun dismissed(daysAgo: Long, lateMinutes: Long, type: AlarmEventType = AlarmEventType.DISMISSED): AlarmHistoryEvent {
        val planned = now.minus(Duration.ofDays(daysAgo)).minus(Duration.ofHours(17))
        return AlarmHistoryEvent(scheduleId = 1, type = type, occurredAt = planned.plusSeconds(lateMinutes * 60), newWakeUp = planned)
    }

    @Test
    fun `평소 늦게 끄면 중앙값을 5분 단위로 올려 준비 시간 늘리기를 제안한다`() {
        val habit = WakeUpHabitPolicy.analyze(listOf(dismissed(1, 12), dismissed(2, 8), dismissed(3, 11), dismissed(4, 2)), now)

        // 2, 8, 11, 12 → 중앙값 11분 → 15분 제안
        assertEquals(WakeUpHabit(samples = 4, typicalLateMinutes = 11, extraPreparationMinutes = 15), habit)
    }

    @Test
    fun `한 번 크게 늦은 날에 끌려가지 않는다`() {
        val habit = WakeUpHabitPolicy.analyze(listOf(dismissed(1, 1), dismissed(2, 2), dismissed(3, 60)), now)

        assertNull(habit)
    }

    @Test
    fun `기록이 3번보다 적거나, 30일보다 오래됐거나, 비정상 값이면 쓰지 않는다`() {
        assertNull(WakeUpHabitPolicy.analyze(listOf(dismissed(1, 20), dismissed(2, 20)), now))
        assertNull(WakeUpHabitPolicy.analyze(listOf(dismissed(1, 20), dismissed(2, 20), dismissed(40, 20)), now))
        assertNull(WakeUpHabitPolicy.analyze(listOf(dismissed(1, 20), dismissed(2, 20), dismissed(3, 200)), now))
    }

    @Test
    fun `알람을 끈 기록만 보고, 제안을 적용하거나 넘긴 뒤의 기록만 다시 모은다`() {
        val events = listOf(dismissed(1, 20), dismissed(2, 20), dismissed(3, 20), dismissed(1, 20, AlarmEventType.FIRED))
        assertEquals(20, WakeUpHabitPolicy.analyze(events, now)?.typicalLateMinutes)

        assertNull(WakeUpHabitPolicy.analyze(events, now, since = now.minus(Duration.ofDays(2))))
    }

    @Test
    fun `제안은 30분을 넘지 않는다`() {
        val habit = WakeUpHabitPolicy.analyze(listOf(dismissed(1, 50), dismissed(2, 55), dismissed(3, 60)), now)

        assertEquals(30, habit?.extraPreparationMinutes)
    }
}
