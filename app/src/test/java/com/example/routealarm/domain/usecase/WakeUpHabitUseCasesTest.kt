package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.repository.HabitRepository
import com.example.routealarm.fake.FakeAlarmHistoryRepository
import com.example.routealarm.fake.FakeAlarmScheduler
import com.example.routealarm.fake.FakeRefreshScheduler
import com.example.routealarm.fake.FakeScheduleRepository
import com.example.routealarm.fake.FakeSubscriptionRepository
import com.example.routealarm.fake.FakeTimeProvider
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class WakeUpHabitUseCasesTest {

    private class FakeHabitRepository : HabitRepository {
        val resets = MutableStateFlow<Map<Long, Instant>>(emptyMap())
        override fun resetAt(scheduleId: Long): Flow<Instant?> = resets.map { it[scheduleId] }
        override suspend fun reset(scheduleId: Long, at: Instant) {
            resets.value = resets.value + (scheduleId to at)
        }
    }

    private val monday = LocalDate.of(2026, 10, 5)
    private val time = FakeTimeProvider(seoul(monday, 5, 0))
    private val schedules = FakeScheduleRepository()
    private val alarmScheduler = FakeAlarmScheduler()
    private val history = FakeAlarmHistoryRepository()
    private val habits = FakeHabitRepository()
    private val scheduleAlarm = ScheduleAlarmUseCase(
        alarmScheduler, FakeRefreshScheduler(), FakeSubscriptionRepository(FakeSubscriptionRepository.PRO), time,
    )
    private val sync = SyncScheduleUseCase(schedules, ResolveNextOccurrenceUseCase(), TestData.buildAlarmPlanUseCase(), scheduleAlarm, time)
    private val apply = ApplyWakeUpHabitUseCase(schedules, habits, scheduleAlarm, time)
    private val observe = ObserveWakeUpHabitUseCase(history, habits, time)

    @Test
    fun `준비 시간을 늘리면 같은 회차의 기상 알람이 그만큼 앞당겨지고, 제안은 다시 나오지 않는다`() = runTest {
        val saved = TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY))
        val id = (schedules.upsert(saved) as AppResult.Success).data
        val synced = sync(saved.copy(id = id))
        // 60분(기본) + 여유 10 + 비실시간 5 + 준비 30 = 105분 전
        assertEquals(seoul(monday, 7, 15), synced.plan!!.wakeUp)

        // 지난 3번 모두 예정보다 12분 늦게 알람을 껐다
        repeat(3) { day ->
            val planned = seoul(monday.minusDays(day + 1L), 7, 15)
            history.record(AlarmHistoryEvent(scheduleId = id, type = AlarmEventType.DISMISSED, occurredAt = planned.plusSeconds(12 * 60), newWakeUp = planned))
        }
        val habit = observe(id).first()!!
        assertEquals(15, habit.extraPreparationMinutes)

        apply(id, habit.extraPreparationMinutes)

        val updated = schedules.getSchedule(id)!!
        assertEquals(45, updated.preparationMinutes)
        assertEquals(seoul(monday, 7, 0), updated.plan!!.wakeUp)
        assertEquals(seoul(monday, 7, 0), alarmScheduler.scheduled[id to AlarmType.WAKE_UP])
        assertNull(observe(id).first())
    }
}
