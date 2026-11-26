package com.example.routealarm.shared.trip

import com.example.routealarm.shared.KoreanHolidays
import com.example.routealarm.shared.OccurrenceEngine
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
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

    /** "평일", "매일", "월 수 금", "한 번" */
    fun repeatLabel(repeatMask: Int): String = when (repeatMask and EVERYDAY) {
        0 -> "한 번"
        EVERYDAY -> "매일"
        WEEKDAYS -> "평일"
        0b1100000 -> "주말"
        else -> days(repeatMask).sortedBy { it.ordinal }.joinToString(" ") { LABELS[it.ordinal] }
    }

    private fun days(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()

    private val LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
}
