package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 하루의 기상 기록. 그날 알람이 없었으면 lateMinutes 가 null */
data class DayWake(val date: LocalDate, val lateMinutes: Int?, val snoozes: Int)

/** 최근 기간 알람 기록 요약(기록·통계 화면) */
data class AlarmStats(
    /** 기상 알람을 끈 횟수(아침 수) */
    val mornings: Int,
    /** 예정 기상 시각에서 [AlarmStatsPolicy.ON_TIME_MINUTES] 안에 끈 아침 수 */
    val onTimeMornings: Int,
    /** 알람이 울린 뒤 끄기까지 걸린 시간의 평균(분) */
    val averageLateMinutes: Int,
    val snoozes: Int,
    /** 교통 변화로 알람을 자동 조정했거나 추천을 받아들인 횟수 */
    val adjustments: Int,
    /** 그 조정으로 알람을 앞당긴 시간의 합(분) */
    val adjustedEarlierMinutes: Int,
    /** 최근 [AlarmStatsPolicy.CHART_DAYS] 일, 오래된 날부터 */
    val days: List<DayWake>,
) {
    /** 제시간 기상률(%). 기록이 없으면 null */
    val onTimeRate: Int? get() = if (mornings == 0) null else Math.round(onTimeMornings * 100f / mornings)
}

/**
 * 알람 이력으로 최근 30일 기상·조정 통계를 만든다. 모든 일정을 합쳐서 본다.
 *
 * - 늦게 끈 시간 = 기상 알람을 끈 시각 − 예정 기상 시각(다시 울림 포함). 기상 습관 분석과 같은 기준으로
 *   음수(예정 시각 전에 끈 기록)와 90분을 넘는 값(알람을 켜 두고 자리를 비운 경우 등)은 뺀다.
 * - 날짜는 예정 기상 시각의 날짜로 묶는다. 하루에 알람이 여럿이면 가장 늦게 끈 값을 그날 값으로 본다.
 */
object AlarmStatsPolicy {
    const val ON_TIME_MINUTES = 5L
    const val CHART_DAYS = 14L
    val LOOKBACK: Duration = Duration.ofDays(30)

    fun summarize(events: List<AlarmHistoryEvent>, now: Instant, zone: ZoneId): AlarmStats {
        val from = now.minus(LOOKBACK)
        val recent = events.filter { it.occurredAt.isAfter(from) && !it.occurredAt.isAfter(now) }

        val wakes = recent.filter { it.type == AlarmEventType.DISMISSED }.mapNotNull { event ->
            val planned = event.newWakeUp ?: return@mapNotNull null
            val late = Duration.between(planned, event.occurredAt).toMinutes()
            // 예정 시각보다 먼저 끈 기록(진짜 알람은 울리기 전에 끌 수 없다)과 90분 넘는 값은 뺀다.
            if (late !in 0..WakeUpHabitPolicy.MAX_SAMPLE_MINUTES) null else planned.atZone(zone).toLocalDate() to late
        }
        val snoozeEvents = recent.filter { it.type == AlarmEventType.SNOOZED }
        val adjustments = recent.filter { it.type == AlarmEventType.AUTO_ADJUSTED || it.type == AlarmEventType.ADJUSTMENT_APPLIED }
        val earlier = adjustments.sumOf { event ->
            val previous = event.previousWakeUp ?: return@sumOf 0L
            val next = event.newWakeUp ?: return@sumOf 0L
            Duration.between(next, previous).toMinutes().coerceAtLeast(0)
        }

        val today = now.atZone(zone).toLocalDate()
        val lateByDay = wakes.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.max() }
        val snoozesByDay = snoozeEvents.groupingBy { (it.newWakeUp ?: it.occurredAt).atZone(zone).toLocalDate() }.eachCount()
        val days = (CHART_DAYS - 1 downTo 0).map { back ->
            val date = today.minusDays(back)
            DayWake(date, lateByDay[date]?.toInt(), snoozesByDay[date] ?: 0)
        }

        return AlarmStats(
            mornings = wakes.size,
            onTimeMornings = wakes.count { it.second <= ON_TIME_MINUTES },
            averageLateMinutes = if (wakes.isEmpty()) 0 else Math.round(wakes.map { it.second }.average()).toInt(),
            snoozes = snoozeEvents.size,
            adjustments = adjustments.size,
            adjustedEarlierMinutes = earlier.toInt(),
            days = days,
        )
    }
}
