package com.example.routealarm.alarm.worker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.policy.AdjustmentPolicy
import com.example.routealarm.domain.usecase.EvaluateAlarmAdjustmentUseCase
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import com.example.routealarm.domain.usecase.ResolveNextOccurrenceUseCase
import com.example.routealarm.domain.usecase.ScheduleAlarmUseCase
import com.example.routealarm.domain.usecase.SyncScheduleUseCase
import com.example.routealarm.fake.FakeAlarmHistoryRepository
import com.example.routealarm.fake.FakeAlarmNotifier
import com.example.routealarm.fake.FakeAlarmScheduler
import com.example.routealarm.fake.FakeLocationRepository
import com.example.routealarm.fake.FakeMetricsRepository
import com.example.routealarm.fake.FakePreferencesRepository
import com.example.routealarm.fake.FakeRefreshScheduler
import com.example.routealarm.fake.FakeScheduleRepository
import com.example.routealarm.fake.FakeSubscriptionRepository
import com.example.routealarm.fake.FakeTimeProvider
import com.example.routealarm.fake.FakeTransitRepository
import com.example.routealarm.fake.FakeWeatherRepository
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * TestListenableWorkerBuilder 로 Worker 를 실제 WorkManager 실행 환경처럼 돌려
 * 결과(success / retry / failure)와 부수 효과(알람 등록, 지표 기록)를 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class TransitRefreshWorkerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = LocalDate.of(2026, 10, 6)
    private val time = FakeTimeProvider(seoul(day.minusDays(1), 22, 0))
    private val transit = FakeTransitRepository(travelMinutes = 60, fetchedAt = time.instant)
    private val schedules = FakeScheduleRepository()
    private val alarmScheduler = FakeAlarmScheduler()
    private val metrics = FakeMetricsRepository()
    private val subscription = FakeSubscriptionRepository(FakeSubscriptionRepository.PRO, time)
    private val scheduleAlarm = ScheduleAlarmUseCase(alarmScheduler, FakeRefreshScheduler(), subscription, time)
    private val weather = FakeWeatherRepository()

    private val refresh = RefreshTransitTimeUseCase(
        scheduleRepository = schedules,
        preferencesRepository = FakePreferencesRepository(UserPreferences(autoAdjustment = true)),
        subscriptionRepository = subscription,
        transitRepository = transit,
        weatherRepository = weather,
        locationRepository = FakeLocationRepository(),
        historyRepository = FakeAlarmHistoryRepository(),
        metricsRepository = metrics,
        resolveNextOccurrence = ResolveNextOccurrenceUseCase(),
        buildAlarmPlan = TestData.buildAlarmPlanUseCase(),
        evaluateAdjustment = EvaluateAlarmAdjustmentUseCase(),
        scheduleAlarm = scheduleAlarm,
        syncSchedule = SyncScheduleUseCase(schedules, ResolveNextOccurrenceUseCase(), TestData.buildAlarmPlanUseCase(), scheduleAlarm, time),
        notifier = FakeAlarmNotifier(),
        adjustmentPolicy = AdjustmentPolicy.Default,
        timeProvider = time,
    )

    /** Hilt 대신 Fake 로 만든 UseCase 를 넣어 Worker 를 만든다. */
    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            TransitRefreshWorker(appContext, workerParameters, refresh, metrics)
    }

    private fun worker(scheduleId: Long?, attempt: Int = 0) =
        TestListenableWorkerBuilder<TransitRefreshWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(attempt)
            .apply { if (scheduleId != null) setInputData(workDataOf(TransitRefreshWorker.KEY_SCHEDULE_ID to scheduleId)) }
            .build()

    private fun newSchedule(): Long = runBlocking {
        (schedules.upsert(TestData.schedule(oneTimeDate = day)) as AppResult.Success).data
    }

    @Test
    fun recalculatesAndRegistersAlarms() = runBlocking {
        val id = newSchedule()

        val result = worker(id).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // 09:00 도착, 이동 60분 → 기상 07:20
        assertEquals(seoul(day, 7, 20), alarmScheduler.scheduled[id to AlarmType.WAKE_UP])
        assertNotNull(schedules.getSchedule(id)?.plan)
        assertEquals(listOf(MetricType.REFRESH_WORK_RUN), metrics.recorded.filter { it == MetricType.REFRESH_WORK_RUN })
    }

    @Test
    fun networkFailure_retriesThenGivesUp() = runBlocking {
        val id = newSchedule()
        transit.failWith = AppError.Network

        assertEquals(ListenableWorker.Result.retry(), worker(id, attempt = 0).doWork())
        assertEquals(ListenableWorker.Result.failure(), worker(id, attempt = 3).doWork())
    }

    @Test
    fun missingScheduleId_fails() = runBlocking {
        assertEquals(ListenableWorker.Result.failure(), worker(scheduleId = null).doWork())
    }
}
