package com.example.routealarm.domain.usecase

import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.policy.TravelEstimatePolicy
import java.time.Duration

/**
 * 한 회차가 "끝났다"고 볼 수 있는 시점까지의 시간(목표 도착 기준).
 * 출발 알림을 쓰면 출발 시각, 기상 알람만 쓰면 기상 시각이 그 회차의 마지막 이벤트다.
 */
internal fun Schedule.occurrenceLeadTime(): Duration {
    val travelAndBuffer = plan
        ?.let { Duration.between(it.departure, it.targetArrival) }
        ?.takeUnless { it.isNegative }
        ?: Duration.ofMinutes(TravelEstimatePolicy.FALLBACK_TRAVEL_MINUTES + bufferMinutes)
    return when (alarmMode) {
        AlarmMode.WAKE_UP_AND_DEPARTURE -> travelAndBuffer
        AlarmMode.WAKE_UP_ONLY -> travelAndBuffer.plus(preparation)
    }
}
