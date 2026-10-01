package com.example.routealarm.alarm.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.repository.MetricsRepository
import com.example.routealarm.domain.repository.TransitRefreshScheduler
import com.example.routealarm.domain.usecase.RefreshOutcome
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 교통 정보 재확인 Worker.
 *
 * WorkManager 를 쓰는 이유: 네트워크가 있을 때만 실행되고, 앱이 죽어 있어도 시스템이 실행해 주며,
 * Doze/배터리 정책을 시스템이 알아서 지켜 준다. 대신 실행 시각이 정확하지 않으므로
 * 실제 알람은 AlarmManager 가 담당한다(ScheduleAlarmUseCase).
 *
 * 다음 체크포인트 예약은 UseCase 안의 ScheduleAlarmUseCase 가 수행하므로 Worker 는 한 번 실행하고 끝난다.
 */
@HiltWorker
class TransitRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshTransitTime: RefreshTransitTimeUseCase,
    private val metricsRepository: MetricsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1)
        if (scheduleId < 0) return Result.failure()
        metricsRepository.record(MetricType.REFRESH_WORK_RUN)

        return when (refreshTransitTime(scheduleId)) {
            // 네트워크 실패는 WorkManager 백오프로 몇 번 더 시도한다. 그 사이 알람은 마지막 계산값으로 유지된다.
            is RefreshOutcome.Failed -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        const val KEY_SCHEDULE_ID = "schedule_id"
        private const val MAX_ATTEMPTS = 3
        fun uniqueName(scheduleId: Long) = "transit-refresh-$scheduleId"
    }
}

@Singleton
class WorkManagerRefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : TransitRefreshScheduler {

    override fun scheduleRefresh(scheduleId: Long, runAt: Instant) {
        val delay = Duration.between(timeProvider.now(), runAt).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<TransitRefreshWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(TransitRefreshWorker.KEY_SCHEDULE_ID to scheduleId))
            .addTag(TAG)
            .build()
        // 일정당 하나만 유지: 재계산 때마다 다음 체크포인트로 교체된다.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(TransitRefreshWorker.uniqueName(scheduleId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelRefresh(scheduleId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(TransitRefreshWorker.uniqueName(scheduleId))
    }

    private fun Duration.coerceAtLeast(min: Duration) = if (this < min) min else this

    private companion object {
        const val TAG = "transit-refresh"
    }
}
