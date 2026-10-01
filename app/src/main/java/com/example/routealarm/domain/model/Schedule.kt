package com.example.routealarm.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class AlarmMode { WAKE_UP_ONLY, WAKE_UP_AND_DEPARTURE }

/**
 * "어디서 어디까지 몇 시까지" 라는 사용자 일정.
 *
 * 설계 이유: 알람(Alarm)이 아니라 일정(Schedule)을 중심 개념으로 둔다.
 * 알람 시각은 일정 + 교통 상황으로부터 "계산되는 값"([plan])일 뿐이다.
 * 이렇게 해 두면 나중에 대학교 시간표나 캘린더 일정을 가져와도 Schedule 만 만들면
 * 나머지 계산/알람/갱신 파이프라인을 그대로 재사용할 수 있다.
 */
data class Schedule(
    val id: Long = 0,
    val title: String,
    val origin: Place,
    val destination: Place,
    /** true 면 갱신 시점에 현재 위치를 출발지로 사용한다(실패 시 [origin] 좌표로 대체). */
    val useCurrentLocation: Boolean = false,
    val targetArrivalTime: LocalTime,
    val preparationMinutes: Int,
    val bufferMinutes: Int,
    /** 비어 있으면 [oneTimeDate] 하루만 울리는 일정 */
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val oneTimeDate: LocalDate? = null,
    val alarmMode: AlarmMode = AlarmMode.WAKE_UP_AND_DEPARTURE,
    val enabled: Boolean = true,
    /** 첫 실행 예시 데이터. 실제 사용자 데이터와 구분해 표시/삭제한다. */
    val isDemo: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** 가장 최근 계산된 알람 정보 */
    val plan: AlarmPlan? = null,
    /** 자동 조정 OFF 이거나 변화가 너무 커서 사용자 확인을 기다리는 변경안 */
    val pendingAdjustment: PendingAdjustment? = null,
) {
    val isRepeating: Boolean get() = repeatDays.isNotEmpty()
    val preparation: Duration get() = Duration.ofMinutes(preparationMinutes.toLong())
}

/**
 * 계산된 알람 정보. Room 에 저장되어 오프라인/재부팅 후에도 그대로 복구된다.
 */
data class AlarmPlan(
    val targetArrival: Instant,
    val estimatedArrival: Instant,
    val departure: Instant,
    val wakeUp: Instant,
    val travelMinutes: Int,
    val safetyBufferMinutes: Int,
    val delayMinutes: Int,
    /** 직전 계산의 이동시간. "이전 계산보다 8분 증가" 표시에 사용 */
    val previousTravelMinutes: Int? = null,
    val isRealtime: Boolean,
    val lastUpdatedAt: Instant,
    val route: TransitRoute? = null,
) {
    /** 목표 도착시간보다 얼마나 일찍 도착하는지 (여유시간) */
    val slackMinutes: Long get() = Duration.between(estimatedArrival, targetArrival).toMinutes()

    val travelChangeMinutes: Int? get() = previousTravelMinutes?.let { travelMinutes - it }

    val trafficStatus: TrafficStatus get() = TrafficStatus.from(Duration.ofMinutes(delayMinutes.toLong()))

    fun isStale(now: Instant, threshold: Duration = STALE_THRESHOLD): Boolean =
        Duration.between(lastUpdatedAt, now) > threshold

    companion object {
        /** 이 시간보다 오래된 계산 결과는 화면에 "오래된 정보"로 명확히 표시한다. */
        val STALE_THRESHOLD: Duration = Duration.ofHours(6)
    }
}

enum class AdjustmentKind {
    /** 자동 조정이 꺼져 있어 추천만 하는 경우 */
    SUGGESTED,

    /** 변화가 최대 자동 조정 범위를 넘어 사용자 확인이 필요한 경우 */
    NEEDS_CONFIRMATION,
}

data class PendingAdjustment(
    val kind: AdjustmentKind,
    val proposedPlan: AlarmPlan,
    /** 음수면 알람을 앞당김 */
    val deltaMinutes: Long,
    val createdAt: Instant,
)
