package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.fake.FakeAlarmScheduler
import com.example.routealarm.fake.FakeRefreshScheduler
import com.example.routealarm.fake.FakeScheduleRepository
import com.example.routealarm.fake.FakeSubscriptionRepository
import com.example.routealarm.fake.FakeTimeProvider
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RestoreScheduleUseCaseTest {

    private val monday = LocalDate.of(2026, 10, 5)
    private val time = FakeTimeProvider(seoul(monday, 5, 0))
    private val schedules = FakeScheduleRepository()
    private val alarmScheduler = FakeAlarmScheduler()
    private val refreshScheduler = FakeRefreshScheduler()
    private val scheduleAlarm = ScheduleAlarmUseCase(
        alarmScheduler, refreshScheduler, FakeSubscriptionRepository(FakeSubscriptionRepository.PRO), time,
    )
    private val sync = SyncScheduleUseCase(schedules, ResolveNextOccurrenceUseCase(), TestData.buildAlarmPlanUseCase(), scheduleAlarm, time)
    private val delete = DeleteScheduleUseCase(schedules, alarmScheduler, refreshScheduler)
    private val restore = RestoreScheduleUseCase(schedules, scheduleAlarm, time)

    @Test
    fun `지운 일정을 되돌리면 같은 id 로 다시 저장되고 기상 알람이 다시 등록된다`() = runTest {
        val saved = TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY))
        val id = (schedules.upsert(saved) as AppResult.Success).data
        val snapshot = sync(saved.copy(id = id))
        delete(id)
        assertNull(schedules.getSchedule(id))
        assertNull(alarmScheduler.scheduled[id to AlarmType.WAKE_UP])

        restore(snapshot)

        assertEquals(snapshot.plan, schedules.getSchedule(id)?.plan)
        assertEquals(seoul(monday, 7, 15), alarmScheduler.scheduled[id to AlarmType.WAKE_UP])
    }

    @Test
    fun `바꾼 알람을 되돌리면 바꾸기 전 기상 시각으로 돌아간다`() = runTest {
        val saved = TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY))
        val id = (schedules.upsert(saved) as AppResult.Success).data
        val before = sync(saved.copy(id = id))
        val moved = before.copy(plan = before.plan!!.copy(wakeUp = seoul(monday, 6, 50)))
        schedules.upsert(moved)
        scheduleAlarm(moved)

        restore(before)

        assertEquals(seoul(monday, 7, 15), schedules.getSchedule(id)?.plan?.wakeUp)
        assertEquals(seoul(monday, 7, 15), alarmScheduler.scheduled[id to AlarmType.WAKE_UP])
    }
}
