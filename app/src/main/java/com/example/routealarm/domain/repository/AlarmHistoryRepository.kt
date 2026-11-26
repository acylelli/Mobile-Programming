package com.example.routealarm.domain.repository

import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.MetricsSummary
import com.example.routealarm.domain.policy.TravelObservation
import kotlinx.coroutines.flow.Flow
import java.time.Duration

interface AlarmHistoryRepository {
    suspend fun record(event: AlarmHistoryEvent)
    fun observeHistory(scheduleId: Long, limit: Int = 20): Flow<List<AlarmHistoryEvent>>

    /** 일정별 가장 최근의 알람 조정(자동 조정/추천 승인). 알람 목록의 "08:40 → 08:32로 앞당김" 표시에 사용 */
    fun observeLatestAdjustments(): Flow<Map<Long, AlarmHistoryEvent>>

    /** 최근 계산된 이동시간들. 실시간 ETA 하나만 믿지 않기 위한 안정화 입력값 */
    suspend fun recentTravelDurations(scheduleId: Long, limit: Int): List<Duration>

    /** 기상 알람을 끈 최근 기록. 기상 습관(평소 얼마나 늦게 끄는지) 분석에 쓴다. */
    fun observeDismissals(scheduleId: Long, limit: Int = 30): Flow<List<AlarmHistoryEvent>>

    /** 회차마다 출발 직전 실시간 이동시간 하나를 남긴다(같은 회차는 덮어씀). 요일·시간대별 학습에 쓴다. */
    suspend fun recordTravel(scheduleId: Long, observation: TravelObservation)
    suspend fun travelObservations(scheduleId: Long): List<TravelObservation>
    fun observeTravelObservations(scheduleId: Long): Flow<List<TravelObservation>>
}

/**
 * 기상 습관 제안을 적용하거나 넘긴 시각. 그 뒤의 기록만 다시 모아 같은 제안을 반복하지 않는다.
 * 기기 안에만 저장한다.
 */
interface HabitRepository {
    fun resetAt(scheduleId: Long): Flow<java.time.Instant?>

    suspend fun reset(scheduleId: Long, at: java.time.Instant)
}

interface MetricsRepository {
    suspend fun record(type: MetricType, valueMillis: Long? = null)
    fun observeSummary(): Flow<MetricsSummary>
}
