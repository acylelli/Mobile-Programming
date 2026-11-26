package com.example.routealarm.domain.usecase

import com.example.routealarm.shared.OccurrenceEngine
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinDayOfWeek
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime
import kotlinx.datetime.toKotlinTimeZone
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinDuration
import kotlin.time.toKotlinInstant

/**
 * 일정의 "다음 목표 도착 시각"을 구한다. 계산은 shared 모듈의 [OccurrenceEngine] 이 한다.
 *
 * - 반복 일정: 반복 요일 중 (목표시각 - leadTime) 이 아직 지나지 않은 첫 날.
 *   leadTime 은 그 회차의 마지막 이벤트(출발 알림 또는 기상 알람)까지의 시간이다.
 * - skipHolidays 면 공휴일(대체공휴일 포함)인 날은 건너뛴다(3주까지 본다).
 * - 1회 일정: 지정된 날짜 그대로(과거 여부는 호출자가 판단). 1회 일정은 공휴일이어도 울린다.
 */
class ResolveNextOccurrenceUseCase @Inject constructor() {

    operator fun invoke(
        targetTime: LocalTime,
        repeatDays: Set<DayOfWeek>,
        oneTimeDate: LocalDate?,
        now: Instant,
        zone: ZoneId,
        leadTime: Duration,
        skipHolidays: Boolean = false,
    ): Instant? = OccurrenceEngine.next(
        targetTime = targetTime.toKotlinLocalTime(),
        repeatDays = repeatDays.mapTo(mutableSetOf()) { it.toKotlinDayOfWeek() },
        oneTimeDate = oneTimeDate?.toKotlinLocalDate(),
        now = now.toKotlinInstant(),
        zone = zone.toKotlinTimeZone(),
        leadTime = leadTime.toKotlinDuration(),
        skipHolidays = skipHolidays,
    )?.toJavaInstant()

    /** 1회 일정의 날짜: 오늘 목표시각이 아직 오지 않았으면 오늘, 지났으면 내일 */
    fun resolveOneTimeDate(targetTime: LocalTime, now: Instant, zone: ZoneId): LocalDate =
        OccurrenceEngine.oneTimeDate(targetTime.toKotlinLocalTime(), now.toKotlinInstant(), zone.toKotlinTimeZone())
            .toJavaLocalDate()
}
