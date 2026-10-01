package com.example.routealarm.domain.repository

import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.MetricsSummary
import kotlinx.coroutines.flow.Flow
import java.time.Duration

interface AlarmHistoryRepository {
    suspend fun record(event: AlarmHistoryEvent)
    fun observeHistory(scheduleId: Long, limit: Int = 20): Flow<List<AlarmHistoryEvent>>

    /** 최근 계산된 이동시간들. 실시간 ETA 하나만 믿지 않기 위한 안정화 입력값 */
    suspend fun recentTravelDurations(scheduleId: Long, limit: Int): List<Duration>
}

interface MetricsRepository {
    suspend fun record(type: MetricType, valueMillis: Long? = null)
    fun observeSummary(): Flow<MetricsSummary>
}
