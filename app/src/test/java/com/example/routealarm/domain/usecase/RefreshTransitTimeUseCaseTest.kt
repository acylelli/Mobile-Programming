package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.domain.model.AdjustmentKind
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.policy.AdjustmentPolicy
import com.example.routealarm.fake.FakeAlarmHistoryRepository
import com.example.routealarm.fake.FakeAlarmNotifier
import com.example.routealarm.fake.FakeAlarmScheduler
import com.example.routealarm.fake.FakeLocationRepository
import com.example.routealarm.fake.FakeMetricsRepository
import com.example.routealarm.fake.FakePreferencesRepository
import com.example.routealarm.fake.FakeRefreshScheduler
import com.example.routealarm.fake.FakeScheduleRepository
import com.example.routealarm.fake.FakeTimeProvider
import com.example.routealarm.fake.FakeTransitRepository
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 교통 상황 변화 → 알람 조정 시나리오를 Fake 저장소만으로 끝까지 검증한다.
 */
class RefreshTransitTimeUseCaseTest {

    private val day = LocalDate.of(2026, 10, 6)
    private val time = FakeTimeProvider(seoul(day.minusDays(1), 22, 0))
    private val transit = FakeTransitRepository(travelMinutes = 60, fetchedAt = time.instant)
    private val schedules = FakeScheduleRepository()
    private val alarmScheduler = FakeAlarmScheduler()
    private val refreshScheduler = FakeRefreshScheduler()
    private val notifier = FakeAlarmNotifier()
    private val history = FakeAlarmHistoryRepository()
    private val preferences = FakePreferencesRepository(UserPreferences(autoAdjustment = true))

    private val resolve = ResolveNextOccurrenceUseCase()
    private val buildPlan = TestData.buildAlarmPlanUseCase()
    private val scheduleAlarm = ScheduleAlarmUseCase(alarmScheduler, refreshScheduler, time)
    private val sync = SyncScheduleUseCase(schedules, resolve, buildPlan, scheduleAlarm, time)

    private val refresh = RefreshTransitTimeUseCase(
        scheduleRepository = schedules,
        preferencesRepository = preferences,
        transitRepository = transit,
        locationRepository = FakeLocationRepository(),
        historyRepository = history,
        metricsRepository = FakeMetricsRepository(),
        resolveNextOccurrence = resolve,
        buildAlarmPlan = buildPlan,
        evaluateAdjustment = EvaluateAlarmAdjustmentUseCase(),
        scheduleAlarm = scheduleAlarm,
        syncSchedule = sync,
        notifier = notifier,
        adjustmentPolicy = AdjustmentPolicy.Default,
        timeProvider = time,
    )

    /** 내일 09:00 1회 일정을 60분 이동 기준으로 등록해 둔다 → 기상 07:20 */
    private suspend fun givenScheduledAt0720(): Long {
        val id = (schedules.upsert(TestData.schedule(oneTimeDate = day)) as com.example.routealarm.core.common.AppResult.Success).data
        val outcome = refresh(id)
        assertTrue(outcome is RefreshOutcome.Recalculated)
        assertEquals(seoul(day, 7, 20), schedule(id).plan!!.wakeUp)
        return id
    }

    private suspend fun schedule(id: Long): Schedule = schedules.getSchedule(id)!!

    @Test
    fun `처음 계산하면 기상 알람과 출발 알림이 모두 등록된다`() = runTest {
        val id = givenScheduledAt0720()

        assertEquals(seoul(day, 7, 20), alarmScheduler.scheduled[id to AlarmType.WAKE_UP])
        assertEquals(seoul(day, 7, 40), alarmScheduler.scheduled[id to AlarmType.DEPARTURE_REMINDER])
        assertEquals(seoul(day, 7, 50), alarmScheduler.scheduled[id to AlarmType.DEPARTURE])
        // 다음 교통 재확인은 알람 3시간 전
        assertEquals(seoul(day, 4, 20), refreshScheduler.scheduled[id])
    }

    @Test
    fun `교통 지연으로 5분 이상 앞당겨야 하면 자동 조정하고 알린다`() = runTest {
        val id = givenScheduledAt0720()
        transit.travelMinutes = 75
        transit.delayMinutes = 15

        val outcome = refresh(id)

        assertTrue(outcome is RefreshOutcome.AutoAdjusted)
        assertEquals(-20L, (outcome as RefreshOutcome.AutoAdjusted).deltaMinutes)
        val plan = schedule(id).plan!!
        assertEquals(seoul(day, 7, 0), plan.wakeUp)
        assertEquals(60, plan.previousTravelMinutes)
        assertEquals(seoul(day, 7, 0), alarmScheduler.scheduled[id to AlarmType.WAKE_UP])
        assertEquals(listOf(seoul(day, 7, 20) to seoul(day, 7, 0)), notifier.adjusted)
    }

    @Test
    fun `5분 미만 변화는 알람을 유지하고 예상 도착만 갱신한다`() = runTest {
        val id = givenScheduledAt0720()
        transit.travelMinutes = 63
        transit.delayMinutes = 3

        val outcome = refresh(id)

        assertTrue(outcome is RefreshOutcome.Unchanged)
        val plan = schedule(id).plan!!
        assertEquals(seoul(day, 7, 20), plan.wakeUp)
        assertEquals(63, plan.travelMinutes)
        assertEquals(seoul(day, 8, 53), plan.estimatedArrival)
        assertTrue(notifier.adjusted.isEmpty())
    }

    @Test
    fun `자동 조정이 꺼져 있으면 알람은 그대로 두고 변경을 추천한다`() = runTest {
        preferences.setAutoAdjustment(false)
        val id = givenScheduledAt0720()
        transit.travelMinutes = 75
        transit.delayMinutes = 15

        val outcome = refresh(id)

        assertTrue(outcome is RefreshOutcome.PendingUserDecision)
        val saved = schedule(id)
        assertEquals(seoul(day, 7, 20), saved.plan!!.wakeUp)
        assertEquals(AdjustmentKind.SUGGESTED, saved.pendingAdjustment!!.kind)
        assertEquals(seoul(day, 7, 0), saved.pendingAdjustment!!.proposedPlan.wakeUp)
        assertEquals(listOf(-20L to false), notifier.suggested)
    }

    @Test
    fun `30분을 넘는 급격한 변화는 자동 변경하지 않고 확인을 요청한다`() = runTest {
        val id = givenScheduledAt0720()
        transit.travelMinutes = 100
        transit.delayMinutes = 40

        val outcome = refresh(id)

        assertTrue(outcome is RefreshOutcome.PendingUserDecision)
        assertEquals(seoul(day, 7, 20), schedule(id).plan!!.wakeUp)
        assertEquals(AdjustmentKind.NEEDS_CONFIRMATION, schedule(id).pendingAdjustment!!.kind)
        assertEquals(listOf(-50L to true), notifier.suggested)
    }

    @Test
    fun `추천을 승인하면 알람이 변경된다`() = runTest {
        preferences.setAutoAdjustment(false)
        val id = givenScheduledAt0720()
        transit.travelMinutes = 75
        transit.delayMinutes = 15
        refresh(id)

        val apply = ApplyPendingAdjustmentUseCase(schedules, history, refreshScheduler, scheduleAlarm, time)
        apply(id)

        assertEquals(seoul(day, 7, 0), schedule(id).plan!!.wakeUp)
        assertNull(schedule(id).pendingAdjustment)
        assertEquals(seoul(day, 7, 0), alarmScheduler.scheduled[id to AlarmType.WAKE_UP])
    }

    @Test
    fun `네트워크 실패 시 마지막 계산으로 알람을 유지하고 실시간 아님으로 표시한다`() = runTest {
        val id = givenScheduledAt0720()
        transit.failWith = AppError.Network

        val outcome = refresh(id)

        assertEquals(RefreshOutcome.Failed(AppError.Network), outcome)
        val plan = schedule(id).plan!!
        assertFalse(plan.isRealtime)
        assertEquals(seoul(day, 7, 20), plan.wakeUp)
        assertNotNull(alarmScheduler.scheduled[id to AlarmType.WAKE_UP])
    }

    @Test
    fun `알람 직전 재확인에서 이미 지난 시각이 나오면 즉시 울리도록 당긴다`() = runTest {
        val id = givenScheduledAt0720()
        time.instant = seoul(day, 7, 5)
        transit.travelMinutes = 85
        transit.delayMinutes = 25

        val outcome = refresh(id)

        assertTrue(outcome is RefreshOutcome.AutoAdjusted)
        val wake = schedule(id).plan!!.wakeUp
        assertTrue(wake.isAfter(time.instant))
        assertTrue(wake.isBefore(seoul(day, 7, 6)))
    }
}
