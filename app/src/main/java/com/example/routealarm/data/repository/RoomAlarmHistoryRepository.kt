package com.example.routealarm.data.repository

import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.data.local.db.AlarmHistoryDao
import com.example.routealarm.data.local.db.MetricDao
import com.example.routealarm.data.local.db.MetricEventEntity
import com.example.routealarm.data.mapper.toDomain
import com.example.routealarm.data.mapper.toEntity
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.MetricsSummary
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.MetricsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAlarmHistoryRepository @Inject constructor(
    private val dao: AlarmHistoryDao,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AlarmHistoryRepository {

    override suspend fun record(event: AlarmHistoryEvent) = withContext(ioDispatcher) {
        runCatching {
            dao.insert(event.toEntity())
            dao.deleteOlderThan(timeProvider.now().minus(RETENTION).toEpochMilli())
        }
        Unit
    }

    override fun observeHistory(scheduleId: Long, limit: Int): Flow<List<AlarmHistoryEvent>> =
        dao.observe(scheduleId, limit).map { list -> list.map { it.toDomain() } }

    override suspend fun recentTravelDurations(scheduleId: Long, limit: Int): List<Duration> =
        withContext(ioDispatcher) {
            runCatching { dao.recentTravelMinutes(scheduleId, limit) }.getOrDefault(emptyList())
                .map { Duration.ofMinutes(it.toLong()) }
        }

    private companion object {
        /** 개인정보 최소화: 오래된 이력은 자동 삭제 */
        val RETENTION: Duration = Duration.ofDays(90)
    }
}

@Singleton
class RoomMetricsRepository @Inject constructor(
    private val dao: MetricDao,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MetricsRepository {

    override suspend fun record(type: MetricType, valueMillis: Long?) = withContext(ioDispatcher) {
        runCatching { dao.insert(MetricEventEntity(type = type.name, valueMillis = valueMillis, occurredAt = timeProvider.now().toEpochMilli())) }
        Unit
    }

    override fun observeSummary(): Flow<MetricsSummary> = dao.observeAggregates().map { rows ->
        val byType = rows.associateBy { it.type }
        fun count(type: MetricType) = byType[type.name]?.count ?: 0
        MetricsSummary(
            apiCallCount = count(MetricType.TRANSIT_API_CALL),
            apiFailureCount = count(MetricType.TRANSIT_API_FAILURE),
            averageApiLatencyMillis = byType[MetricType.TRANSIT_API_CALL.name]?.averageValue?.toLong(),
            cacheHitCount = count(MetricType.TRANSIT_CACHE_HIT),
            recalculationCount = count(MetricType.ALARM_RECALCULATION),
            autoAdjustedCount = count(MetricType.ALARM_AUTO_ADJUSTED),
            refreshWorkRunCount = count(MetricType.REFRESH_WORK_RUN),
        )
    }
}
