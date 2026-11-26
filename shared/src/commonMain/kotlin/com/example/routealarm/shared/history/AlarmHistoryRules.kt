package com.example.routealarm.shared.history

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** 알람 이력 한 건(안드로이드 AlarmHistoryEvent 와 같은 뜻). 시각은 epoch 밀리초 */
data class AlarmEvent(
    /** DISMISSED, SNOOZED, AUTO_ADJUSTED, ADJUSTMENT_APPLIED, CREATED, RECALCULATED … */
    val type: String,
    val occurredAtMillis: Long,
    /** 끄기·다시 울림: 예정 기상 시각. 조정: 새 기상 시각 */
    val newWakeUpMillis: Long? = null,
    /** 조정: 이전 기상 시각 */
    val previousWakeUpMillis: Long? = null,
)

/** 하루 기상 기록. 그날 알람이 없었으면 lateMinutes 가 null */
data class DayWake(val month: Int, val day: Int, val weekday: String, val lateMinutes: Int?, val snoozes: Int)

data class AlarmStats(
    val mornings: Int,
    val onTimeMornings: Int,
    val averageLateMinutes: Int,
    val snoozes: Int,
    val adjustments: Int,
    val adjustedEarlierMinutes: Int,
    /** 최근 14일, 오래된 날부터 */
    val days: List<DayWake>,
) {
    /** 제시간 기상률(%). 기록이 없으면 -1 */
    val onTimeRate: Int get() = if (mornings == 0) -1 else (onTimeMornings * 100f / mornings).roundToInt()
}

/** 기상 습관: 평소 알람을 얼마나 늦게 끄는지와 늘리면 좋을 준비 시간 */
data class WakeUpHabit(val samples: Int, val typicalLateMinutes: Int, val extraPreparationMinutes: Int)

/** 지각 위험(택시 안내). reason: TRAFFIC(교통 악화), OVERSLEPT(늦잠) */
data class LateRisk(
    val reason: String,
    val targetMillis: Long,
    val leaveAtMillis: Long,
    val transitArrivalMillis: Long,
    val lateMinutes: Int,
)

/**
 * 알람 이력 규칙(안드로이드 AlarmStatsPolicy · WakeUpHabitPolicy · LateRiskPolicy 와 같은 규칙).
 * - 늦게 끈 시간 = 끈 시각 − 예정 기상 시각. 음수와 90분 넘는 값은 뺀다.
 * - 통계는 최근 30일, 막대는 최근 14일(하루에 여럿이면 가장 늦게 끈 값).
 * - 습관은 최근 30일(제안을 적용·넘긴 뒤의 기록만) 중앙값이 5분 이상이면 5분 단위로 올려 제안(최대 30분).
 */
object AlarmHistoryRules {
    const val ON_TIME_MINUTES = 5
    const val CHART_DAYS = 14
    const val MAX_SAMPLE_MINUTES = 90L
    const val HABIT_MIN_SAMPLES = 3
    const val HABIT_MIN_LATE = 5
    const val HABIT_MAX_EXTRA = 30
    private val LOOKBACK = 30.days
    private val LATE_WINDOW = 3.hours
    private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    fun stats(events: List<AlarmEvent>, nowMillis: Long): AlarmStats {
        val zone = TimeZone.currentSystemDefault()
        val now = Instant.fromEpochMilliseconds(nowMillis)
        val from = now - LOOKBACK
        val recent = events.filter { val at = at(it); at > from && at <= now }
        val wakes = recent.filter { it.type == "DISMISSED" }.mapNotNull { event ->
            val planned = event.newWakeUpMillis ?: return@mapNotNull null
            val late = (event.occurredAtMillis - planned) / 60_000
            if (late !in 0..MAX_SAMPLE_MINUTES) null
            else Instant.fromEpochMilliseconds(planned).toLocalDateTime(zone).date to late
        }
        val snoozes = recent.filter { it.type == "SNOOZED" }
        val adjustments = recent.filter { it.type == "AUTO_ADJUSTED" || it.type == "ADJUSTMENT_APPLIED" }
        val earlier = adjustments.sumOf { event ->
            val previous = event.previousWakeUpMillis ?: return@sumOf 0L
            val next = event.newWakeUpMillis ?: return@sumOf 0L
            ((previous - next) / 60_000).coerceAtLeast(0)
        }
        val today = now.toLocalDateTime(zone).date
        val lateByDay = wakes.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.max() }
        val snoozesByDay = snoozes.groupingBy {
            Instant.fromEpochMilliseconds(it.newWakeUpMillis ?: it.occurredAtMillis).toLocalDateTime(zone).date
        }.eachCount()
        val days = (CHART_DAYS - 1 downTo 0).map { back ->
            val date: LocalDate = today.minus(DatePeriod(days = back))
            DayWake(date.month.number, date.day, WEEKDAYS[date.dayOfWeek.ordinal], lateByDay[date]?.toInt(), snoozesByDay[date] ?: 0)
        }
        return AlarmStats(
            mornings = wakes.size,
            onTimeMornings = wakes.count { it.second <= ON_TIME_MINUTES },
            averageLateMinutes = if (wakes.isEmpty()) 0 else wakes.map { it.second }.average().roundToInt(),
            snoozes = snoozes.size,
            adjustments = adjustments.size,
            adjustedEarlierMinutes = earlier.toInt(),
            days = days,
        )
    }

    /** sinceMillis: 제안을 적용하거나 넘긴 시각(그 뒤 기록만 다시 모은다). 없으면 0 */
    fun habit(events: List<AlarmEvent>, nowMillis: Long, sinceMillis: Long): WakeUpHabit? {
        val from = maxOf(nowMillis - LOOKBACK.inWholeMilliseconds, sinceMillis)
        val late = events.asSequence()
            .filter { it.type == "DISMISSED" && it.occurredAtMillis > from }
            .mapNotNull { event -> event.newWakeUpMillis?.let { (event.occurredAtMillis - it) / 60_000 } }
            .filter { it in 0..MAX_SAMPLE_MINUTES }
            .sorted().toList()
        if (late.size < HABIT_MIN_SAMPLES) return null
        val median = late[late.size / 2].toInt()
        if (median < HABIT_MIN_LATE) return null
        val extra = (ceil(median / 5.0) * 5).toInt().coerceAtMost(HABIT_MAX_EXTRA)
        return WakeUpHabit(late.size, median, extra)
    }

    /**
     * 지각 위험: 늦잠(이 회차 알람을 끈 시각 + 준비 시간에 나가면 늦음) 먼저, 아니면 교통 악화(출발 전인데 도착 예상이 목표보다 늦음).
     * 목표 도착 3시간 전부터 목표 도착 전까지만 본다.
     */
    fun lateRisk(
        targetArrivalMillis: Long,
        departureMillis: Long,
        estimatedArrivalMillis: Long,
        travelMinutes: Int,
        targetHour: Int,
        targetMinute: Int,
        preparationMinutes: Int,
        lastDismissal: AlarmEvent?,
        nowMillis: Long,
    ): LateRisk? {
        val now = Instant.fromEpochMilliseconds(nowMillis)
        overslept(travelMinutes, targetHour, targetMinute, preparationMinutes, lastDismissal, now)?.let { return it }
        val target = Instant.fromEpochMilliseconds(targetArrivalMillis)
        if (!inWindow(target, now) || nowMillis > departureMillis) return null
        val late = ((estimatedArrivalMillis - targetArrivalMillis) / 60_000).toInt()
        return if (late > 0) LateRisk("TRAFFIC", targetArrivalMillis, departureMillis, estimatedArrivalMillis, late) else null
    }

    private fun overslept(travelMinutes: Int, hour: Int, minute: Int, preparation: Int, dismissal: AlarmEvent?, now: Instant): LateRisk? {
        if (dismissal == null || dismissal.type != "DISMISSED") return null
        val plannedWake = Instant.fromEpochMilliseconds(dismissal.newWakeUpMillis ?: return null)
        val zone = TimeZone.currentSystemDefault()
        var target = LocalDateTime(plannedWake.toLocalDateTime(zone).date, LocalTime(hour, minute)).toInstant(zone)
        if (target <= plannedWake) target += 1.days
        val dismissedAt = Instant.fromEpochMilliseconds(dismissal.occurredAtMillis)
        if (!inWindow(target, now) || dismissedAt > target) return null
        val leaveAt = dismissedAt + preparation.minutes
        val arrival = leaveAt + travelMinutes.minutes
        val late = (arrival - target).inWholeMinutes.toInt()
        return if (late > 0) {
            LateRisk("OVERSLEPT", target.toEpochMilliseconds(), leaveAt.toEpochMilliseconds(), arrival.toEpochMilliseconds(), late)
        } else {
            null
        }
    }

    private fun inWindow(target: Instant, now: Instant) = now < target && now >= target - LATE_WINDOW

    private fun at(event: AlarmEvent) = Instant.fromEpochMilliseconds(event.occurredAtMillis)
}
