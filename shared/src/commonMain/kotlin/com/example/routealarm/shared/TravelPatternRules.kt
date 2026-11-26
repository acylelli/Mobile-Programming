package com.example.routealarm.shared

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** 한 회차에서 출발 직전에 확인한 실제 이동시간 */
data class TravelSample(val departure: Instant, val travelMinutes: Int)

/** 요일 하나의 이동시간 요약 */
data class WeekdayTravelSummary(val day: DayOfWeek, val samples: Int, val typicalMinutes: Int, val extraMinutes: Int)

data class TravelPatternSummary(val baselineMinutes: Int, val weekdays: List<WeekdayTravelSummary>) {
    /** 평소보다 눈에 띄게 오래 걸리는 요일 중 가장 큰 것 */
    val slowest: WeekdayTravelSummary?
        get() = weekdays.filter { it.extraMinutes >= TravelPatternRules.NOTABLE_EXTRA_MINUTES }.maxByOrNull { it.extraMinutes }
}

/**
 * 요일·시간대별 실제 이동시간 학습.
 * 같은 요일이고 출발 시각이 1시간 안인 최근 8주 기록이 3번 이상이면 "같은 시간대"로 보고
 * 이동시간 안정화의 과거값으로 쓴다. 모자라면 빈 목록(요일과 상관없는 최근 기록을 쓰게 한다).
 */
object TravelPatternRules {
    const val MIN_SAMPLES = 3
    const val NOTABLE_EXTRA_MINUTES = 3
    val SLOT_WINDOW: Duration = 60.minutes
    val LOOKBACK: Duration = 56.days

    fun slotSamples(samples: List<TravelSample>, departure: Instant, now: Instant, zone: TimeZone): List<Duration> {
        val target = departure.toLocalDateTime(zone)
        val matched = recent(samples, now).filter {
            val at = it.departure.toLocalDateTime(zone)
            at.dayOfWeek == target.dayOfWeek && minutesApart(at.time, target.time) <= SLOT_WINDOW.inWholeMinutes
        }
        return if (matched.size >= MIN_SAMPLES) matched.map { it.travelMinutes.minutes } else emptyList()
    }

    fun summary(samples: List<TravelSample>, now: Instant, zone: TimeZone): TravelPatternSummary? {
        val recent = recent(samples, now)
        if (recent.size < MIN_SAMPLES) return null
        val baseline = median(recent.map { it.travelMinutes })
        val weekdays = recent
            .groupBy { it.departure.toLocalDateTime(zone).dayOfWeek }
            .filterValues { it.size >= MIN_SAMPLES }
            .map { (day, list) ->
                val typical = median(list.map { it.travelMinutes })
                WeekdayTravelSummary(day, list.size, typical, typical - baseline)
            }
            .sortedBy { it.day.ordinal }
        return if (weekdays.isEmpty()) null else TravelPatternSummary(baseline, weekdays)
    }

    private fun recent(samples: List<TravelSample>, now: Instant) =
        samples.filter { it.departure <= now && it.departure > now - LOOKBACK && it.travelMinutes > 0 }

    private fun minutesApart(a: LocalTime, b: LocalTime): Long {
        val diff = abs(a.toSecondOfDay() - b.toSecondOfDay()) / 60L
        return minOf(diff, MINUTES_PER_DAY - diff)
    }

    /** 짝수 개면 가운데 두 값 중 큰 값(일찍 깨우는 쪽이 안전하다) */
    private fun median(values: List<Int>): Int = values.sorted().let { it[it.size / 2] }

    private const val MINUTES_PER_DAY = 24 * 60L
}
