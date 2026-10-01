package com.example.routealarm.domain.usecase

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * 일정의 "다음 목표 도착 시각"을 구한다.
 *
 * - 반복 일정: 오늘부터 7일 뒤까지 반복 요일 중, (목표시각 - leadTime) 이 아직 지나지 않은 첫 날.
 *   leadTime 은 그 회차의 마지막 이벤트(출발 알림 또는 기상 알람)까지의 시간이다.
 *   즉 오늘 회차의 마지막 알림이 지나야 다음 회차로 넘어간다.
 * - 1회 일정: 지정된 날짜 그대로(과거 여부는 호출자가 판단).
 *
 * ZonedDateTime.of 는 DST 로 존재하지 않는 시각(봄 전환의 02:30 등)을 자동으로 뒤로 밀어준다.
 */
class ResolveNextOccurrenceUseCase @Inject constructor() {

    operator fun invoke(
        targetTime: LocalTime,
        repeatDays: Set<DayOfWeek>,
        oneTimeDate: LocalDate?,
        now: Instant,
        zone: ZoneId,
        leadTime: Duration,
    ): Instant? {
        if (repeatDays.isEmpty()) {
            return oneTimeDate?.let { ZonedDateTime.of(it, targetTime, zone).toInstant() }
        }
        val today = now.atZone(zone).toLocalDate()
        return (0L..DAYS_TO_SEARCH)
            .asSequence()
            .map { today.plusDays(it) }
            .filter { it.dayOfWeek in repeatDays }
            .map { ZonedDateTime.of(it, targetTime, zone).toInstant() }
            .firstOrNull { it.minus(leadTime).isAfter(now) }
    }

    /**
     * 1회 일정의 날짜를 정한다. 오늘 목표시각이 아직 오지 않았으면 오늘, 지났으면 내일.
     * (오늘이 빠듯한 경우는 "도착하기 어려워요" 결과로 사용자에게 보여준다.)
     */
    fun resolveOneTimeDate(targetTime: LocalTime, now: Instant, zone: ZoneId): LocalDate {
        val today = now.atZone(zone).toLocalDate()
        val todayTarget = ZonedDateTime.of(today, targetTime, zone).toInstant()
        return if (todayTarget.isAfter(now)) today else today.plusDays(1)
    }

    private companion object {
        /** 오늘 + 다음 주 같은 요일까지 */
        const val DAYS_TO_SEARCH = 7L
    }
}
