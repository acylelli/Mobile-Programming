package com.example.routealarm.shared.trip

import com.example.routealarm.shared.KoreanHolidays
import com.example.routealarm.shared.OccurrenceEngine
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * 반복 일정 계산(Swift 용 단순한 입력). 요일은 비트마스크: 월=1, 화=2, 수=4 … 일=64. 0 이면 1회 일정.
 * 시간대는 기기 설정을 따른다.
 */
object Schedules {
    const val WEEKDAYS = 0b0011111
    const val EVERYDAY = 0b1111111

    /**
     * 다음 목표 도착 시각(epoch 밀리초). leadMinutes 는 목표 시각보다 얼마나 먼저 알람이 울리는지(이동+여유+준비).
     * 1회 일정이면 목표 시각이 아직 안 왔으면 오늘, 지났으면 내일. 찾지 못하면 null.
     */
    fun nextTargetMillis(
        hour: Int,
        minute: Int,
        repeatMask: Int,
        nowMillis: Long,
        leadMinutes: Int,
        skipHolidays: Boolean,
    ): Long? {
        val now = Instant.fromEpochMilliseconds(nowMillis)
        val zone = TimeZone.currentSystemDefault()
        val time = LocalTime(hour, minute)
        val days = days(repeatMask)
        val oneTime = if (days.isEmpty()) OccurrenceEngine.oneTimeDate(time, now, zone) else null
        return OccurrenceEngine.next(time, days, oneTime, now, zone, leadMinutes.minutes, skipHolidays)?.toEpochMilliseconds()
    }

    /** 그날이 공휴일이면 이름, 아니면 null */
    fun holidayName(millis: Long): String? =
        KoreanHolidays.name(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date)

    /** 안드로이드 repeatText 와 같은 규칙: "평일", "매일", "주말", "화, 목", "한 번" */
    fun repeatLabel(repeatMask: Int): String = when (repeatMask and EVERYDAY) {
        0 -> "한 번"
        EVERYDAY -> "매일"
        WEEKDAYS -> "평일"
        0b1100000 -> "주말"
        else -> days(repeatMask).sortedBy { it.ordinal }.joinToString(", ") { LABELS[it.ordinal] }
    }

    /** 알람 머리말: "평일 반복", "화, 목 반복", 1회면 "11월 27일 한 번" */
    fun repeatHeader(repeatMask: Int, targetMillis: Long?): String {
        if (repeatMask and EVERYDAY == 0) {
            val date = targetMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date }
            return if (date == null) "한 번" else "${date.month.number}월 ${date.day}일 한 번"
        }
        return "${repeatLabel(repeatMask)} 반복"
    }

    /**
     * 공휴일 건너뛰기가 켜진 반복 일정에서 오늘부터 31일 안에 반복 요일과 겹치는 첫 공휴일(안드로이드 HolidaySkipPolicy).
     * 오늘 목표 시각이 지났으면 오늘은 보지 않는다. 결과 예: "12월 25일 (금) 성탄절"
     */
    fun nextSkippedHoliday(hour: Int, minute: Int, repeatMask: Int, skipHolidays: Boolean, nowMillis: Long): String? {
        val days = days(repeatMask)
        if (days.isEmpty() || !skipHolidays) return null
        val zone = TimeZone.currentSystemDefault()
        val now = Instant.fromEpochMilliseconds(nowMillis)
        val today = now.toLocalDateTime(zone).date
        val targetPassedToday = LocalDateTime(today, LocalTime(hour, minute)).toInstant(zone) <= now
        return (0..LOOKAHEAD_DAYS).asSequence()
            .map { today.plus(DatePeriod(days = it)) }
            .filterNot { it == today && targetPassedToday }
            .filter { it.dayOfWeek in days }
            .firstNotNullOfOrNull { date ->
                KoreanHolidays.name(date)?.let { "${date.month.number}월 ${date.day}일 (${LABELS[date.dayOfWeek.ordinal]}) $it" }
            }
    }

    private const val LOOKAHEAD_DAYS = 31

    private fun days(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()

    private val LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
}
