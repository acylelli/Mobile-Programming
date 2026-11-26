package com.example.routealarm.shared

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * 일정의 다음 목표 도착 시각.
 *
 * - 반복 일정: 오늘부터 3주 안의 반복 요일 중 (목표 시각 - leadTime) 이 아직 지나지 않은 첫 날.
 *   leadTime 은 그 회차의 마지막 이벤트(출발 알림 또는 기상 알람)까지의 시간이다.
 * - skipHolidays 면 공휴일(대체공휴일 포함)은 건너뛴다. 1회 일정은 공휴일에도 울린다.
 * - 1회 일정: 지정된 날짜 그대로(과거 여부는 호출자가 판단).
 */
object OccurrenceEngine {
    private const val DAYS_TO_SEARCH = 21

    fun next(
        targetTime: LocalTime,
        repeatDays: Set<DayOfWeek>,
        oneTimeDate: LocalDate?,
        now: Instant,
        zone: TimeZone,
        leadTime: Duration,
        skipHolidays: Boolean = false,
    ): Instant? {
        if (repeatDays.isEmpty()) return oneTimeDate?.let { LocalDateTime(it, targetTime).toInstant(zone) }
        val today = now.toLocalDateTime(zone).date
        return (0..DAYS_TO_SEARCH)
            .asSequence()
            .map { today.plus(DatePeriod(days = it)) }
            .filter { it.dayOfWeek in repeatDays }
            .filterNot { skipHolidays && KoreanHolidays.isHoliday(it) }
            .map { LocalDateTime(it, targetTime).toInstant(zone) }
            .firstOrNull { it - leadTime > now }
    }

    /** 1회 일정의 날짜: 오늘 목표 시각이 아직 오지 않았으면 오늘, 지났으면 내일 */
    fun oneTimeDate(targetTime: LocalTime, now: Instant, zone: TimeZone): LocalDate {
        val today = now.toLocalDateTime(zone).date
        val todayTarget = LocalDateTime(today, targetTime).toInstant(zone)
        return if (todayTarget > now) today else today.plus(DatePeriod(days = 1))
    }
}
