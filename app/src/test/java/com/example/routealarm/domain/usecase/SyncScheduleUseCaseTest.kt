package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.fake.FakeAlarmScheduler
import com.example.routealarm.fake.FakeRefreshScheduler
import com.example.routealarm.fake.FakeScheduleRepository
import com.example.routealarm.fake.FakeTimeProvider
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SyncScheduleUseCaseTest {

    private val monday = LocalDate.of(2026, 10, 5)
    private val time = FakeTimeProvider(seoul(monday, 5, 0))
    private val schedules = FakeScheduleRepository()
    private val alarmScheduler = FakeAlarmScheduler()
    private val refreshScheduler = FakeRefreshScheduler()
    private val scheduleAlarm = ScheduleAlarmUseCase(alarmScheduler, refreshScheduler, time)
    private val sync = SyncScheduleUseCase(
        schedules, ResolveNextOccurrenceUseCase(), TestData.buildAlarmPlanUseCase(), scheduleAlarm, time,
    )

    private suspend fun save(schedule: com.example.routealarm.domain.model.Schedule) =
        schedule.copy(id = (schedules.upsert(schedule) as AppResult.Success).data)

    @Test
    fun `재부팅 후 계획이 없으면 오프라인 기본값으로 계산해 알람을 복구한다`() = runTest {
        val schedule = save(TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY)))

        val synced = sync(schedule)

        val plan = synced.plan!!
        assertFalse(plan.isRealtime)
        assertEquals(seoul(monday, 9, 0), plan.targetArrival)
        // 60분(기본) + 여유 10 + 비실시간 5 + 준비 30 = 105분 전
        assertEquals(seoul(monday, 7, 15), plan.wakeUp)
        assertEquals(seoul(monday, 7, 15), alarmScheduler.scheduled[schedule.id to AlarmType.WAKE_UP])
    }

    @Test
    fun `출발 알림까지 끝나면 다음 반복 요일 회차로 넘어간다`() = runTest {
        val schedule = save(TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)))
        val first = sync(schedule)
        time.instant = first.plan!!.departure.plusSeconds(60)

        val next = sync(first)

        assertEquals(seoul(monday.plusDays(1), 9, 0), next.plan!!.targetArrival)
    }

    @Test
    fun `기상 알람만 쓰는 일정은 기상 이후 다음 회차로 넘어간다`() = runTest {
        val schedule = save(
            TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY), alarmMode = AlarmMode.WAKE_UP_ONLY),
        )
        val first = sync(schedule)
        time.instant = first.plan!!.wakeUp.plusSeconds(60)

        val next = sync(first)

        assertEquals(seoul(monday.plusWeeks(1), 9, 0), next.plan!!.targetArrival)
    }

    @Test
    fun `지난 1회 일정은 비활성화되고 알람이 취소된다`() = runTest {
        val schedule = save(TestData.schedule(oneTimeDate = monday))
        sync(schedule)
        time.instant = seoul(monday, 9, 30)

        val result = sync(schedules.getSchedule(schedule.id)!!)

        assertFalse(result.enabled)
        assertTrue(alarmScheduler.scheduled.isEmpty())
    }

    @Test
    fun `시간대가 바뀌면 새 시간대의 09시 기준으로 다시 계산한다`() = runTest {
        val schedule = save(TestData.schedule(repeatDays = DayOfWeek.entries.toSet()))
        val seoulPlan = sync(schedule).plan!!

        time.zoneId = ZoneId.of("Asia/Tokyo") // 같은 UTC+9 지만 별도 Zone
        time.zoneId = ZoneId.of("Asia/Bangkok") // UTC+7
        val bangkokPlan = sync(schedules.getSchedule(schedule.id)!!).plan!!

        assertEquals(LocalTime.of(9, 0), bangkokPlan.targetArrival.atZone(time.zoneId).toLocalTime())
        assertEquals(Duration.ofHours(2), Duration.between(seoulPlan.targetArrival, bangkokPlan.targetArrival))
    }

    @Test
    fun `정확한 알람 권한이 없으면 근사 알람으로 등록되었음을 알려준다`() = runTest {
        alarmScheduler.exactAllowed = false
        val schedule = save(TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY)))
        val synced = sync(schedule)

        val registration = scheduleAlarm(synced)

        assertFalse(registration.exact)
        assertTrue(AlarmType.WAKE_UP in registration.registeredTypes)
    }

    @Test
    fun `재계산해도 사용자가 누른 스누즈는 취소되지 않는다`() = runTest {
        val schedule = save(TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY)))
        val synced = sync(schedule)
        alarmScheduler.schedule(schedule.id, AlarmType.SNOOZE, time.instant.plusSeconds(300))

        scheduleAlarm(synced)

        assertTrue((schedule.id to AlarmType.SNOOZE) in alarmScheduler.scheduled)
    }
}
