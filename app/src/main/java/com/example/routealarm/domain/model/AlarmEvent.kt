package com.example.routealarm.domain.model

import java.time.Instant

enum class AlarmType(val requestCodeOffset: Int) {
    WAKE_UP(0),
    DEPARTURE_REMINDER(1),
    DEPARTURE(2),
    SNOOZE(3),
}

enum class AlarmEventType {
    CREATED,
    RECALCULATED,
    AUTO_ADJUSTED,
    ADJUSTMENT_SUGGESTED,
    ADJUSTMENT_APPLIED,
    FIRED,
    SNOOZED,
    DISMISSED,
    REFRESH_FAILED,
}

/**
 * 알람 이력. 통계(재계산 횟수, 예측 오차)와 "평균 이동시간" 기반 안정화에 사용한다.
 * 개인정보 최소화를 위해 좌표/주소는 저장하지 않는다.
 */
data class AlarmHistoryEvent(
    val id: Long = 0,
    val scheduleId: Long,
    val type: AlarmEventType,
    val occurredAt: Instant,
    val previousWakeUp: Instant? = null,
    val newWakeUp: Instant? = null,
    val travelMinutes: Int? = null,
    val isRealtime: Boolean? = null,
)
