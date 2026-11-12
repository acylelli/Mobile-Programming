package com.example.routealarm.domain.repository

import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.MetricsSummary
import kotlinx.coroutines.flow.Flow
import java.time.Duration

interface AlarmHistoryRepository {
    suspend fun record(event: AlarmHistoryEvent)
    fun observeHistory(scheduleId: Long, limit: Int = 20): Flow<List<AlarmHistoryEvent>>

    /** 일정별 가장 최근의 알람 조정(자동 조정/추천 승인). 알람 목록의 "08:40 → 08:32로 앞당김" 표시에 사용 */
    fun observeLatestAdjustments(): Flow<Map<Long, AlarmHistoryEvent>>

    /** 최근 계산된 이동시간들. 실시간 ETA 하나만 믿지 않기 위한 안정화 입력값 */
    suspend fun recentTravelDurations(scheduleId: Long, limit: Int): List<Duration>
}

interface MetricsRepository {
    suspend fun record(type: MetricType, valueMillis: Long? = null)
    fun observeSummary(): Flow<MetricsSummary>
}
