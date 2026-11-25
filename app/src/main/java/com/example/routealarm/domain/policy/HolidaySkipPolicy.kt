package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 상세 화면 안내용: 곧 건너뛸 공휴일 하나 */
data class SkippedHoliday(val date: LocalDate, val name: String)

object HolidaySkipPolicy {
    /** 앞으로 며칠 안의 건너뛸 공휴일을 보여 줄지 */
    const val LOOKAHEAD_DAYS = 31L

    /**
     * 공휴일 건너뛰기가 켜진 반복 일정에서, 오늘부터 [LOOKAHEAD_DAYS] 안에 반복 요일과 겹치는 첫 공휴일.
     * 이미 지난 오늘 회차는 다루지 않으므로 오늘 날짜는 목표 시각이 아직 남아 있을 때만 본다.
     */
    fun nextSkipped(schedule: Schedule, now: Instant, zone: ZoneId): SkippedHoliday? {
        if (!schedule.isRepeating || !schedule.skipHolidays) return null
        val today = now.atZone(zone).toLocalDate()
        val targetPassedToday = !now.isBefore(today.atTime(schedule.targetArrivalTime).atZone(zone).toInstant())
        return (0L..LOOKAHEAD_DAYS)
            .asSequence()
            .map(today::plusDays)
            .filterNot { it == today && targetPassedToday }
            .filter { it.dayOfWeek in schedule.repeatDays }
            .firstNotNullOfOrNull { date -> KoreanHolidays.name(date)?.let { SkippedHoliday(date, it) } }
    }
}
