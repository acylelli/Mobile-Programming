package com.example.routealarm.domain.policy

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * 한 회차에서 출발 직전에 확인한 실제 이동시간. 회차마다 마지막 확인 값 하나만 남는다.
 * (실시간 교통 정보로 다시 계산할 때마다 같은 회차의 값을 덮어쓰므로, 출발에 가장 가까운 값이 남는다.)
 */
data class TravelObservation(
    val occurrenceTarget: Instant,
    val departure: Instant,
    val travelMinutes: Int,
)

/** 요일 하나의 이동시간 요약(상세 화면 카드) */
data class WeekdayTravel(
    val day: DayOfWeek,
    val samples: Int,
    val typicalMinutes: Int,
    /** 전체 평소 값보다 몇 분 더(음수면 덜) 걸리는지 */
    val extraMinutes: Int,
)

data class TravelPattern(
    val baselineMinutes: Int,
    val weekdays: List<WeekdayTravel>,
) {
    /** 평소보다 눈에 띄게 오래 걸리는 요일 중 가장 큰 것 */
    val slowest: WeekdayTravel?
        get() = weekdays.filter { it.extraMinutes >= TravelPatternPolicy.NOTABLE_EXTRA_MINUTES }.maxByOrNull { it.extraMinutes }
}

/**
 * 요일·시간대별 실제 이동시간 학습.
 *
 * - 같은 요일이고 출발 시각이 [SLOT_WINDOW] 안인 최근 [LOOKBACK] 기록을 "같은 시간대"로 본다.
 * - 같은 시간대 기록이 [MIN_SAMPLES] 이상이면 그 기록을 이동시간 안정화(EstimateTravelTime)의 과거값으로 쓴다.
 *   예: 월요일 아침만 늘 막히면, 월요일 회차는 월요일 기록과 섞여 조금 더 일찍 깨운다.
 *   안정화는 현재 ETA 보다 짧아지지 않으므로, 평소보다 빠른 요일이라고 늦게 깨우지는 않는다.
 * - 기록이 모자라면 기존처럼 요일과 상관없는 최근 기록을 쓴다.
 */
object TravelPatternPolicy {
    const val MIN_SAMPLES = 3
    const val NOTABLE_EXTRA_MINUTES = 3
    val SLOT_WINDOW: Duration = Duration.ofMinutes(60)
    val LOOKBACK: Duration = Duration.ofDays(56)

    /** departure 와 같은 요일·시간대의 최근 기록. 모자라면 빈 목록 */
    fun slotSamples(
        observations: List<TravelObservation>,
        departure: Instant,
        now: Instant,
        zone: ZoneId,
    ): List<Duration> {
        val target = departure.atZone(zone)
        val samples = recent(observations, now).filter {
            val at = it.departure.atZone(zone)
            at.dayOfWeek == target.dayOfWeek && minutesApart(at.toLocalTime(), target.toLocalTime()) <= SLOT_WINDOW.toMinutes()
        }
        return if (samples.size >= MIN_SAMPLES) samples.map { Duration.ofMinutes(it.travelMinutes.toLong()) } else emptyList()
    }

    /** 요일별 요약. 기록이 [MIN_SAMPLES] 이상인 요일만 넣고, 그런 요일이 없으면 null */
    fun pattern(observations: List<TravelObservation>, now: Instant, zone: ZoneId): TravelPattern? {
        val recent = recent(observations, now)
        if (recent.size < MIN_SAMPLES) return null
        val baseline = median(recent.map { it.travelMinutes })
        val weekdays = recent
            .groupBy { it.departure.atZone(zone).dayOfWeek }
            .filterValues { it.size >= MIN_SAMPLES }
            .map { (day, list) ->
                val typical = median(list.map { it.travelMinutes })
                WeekdayTravel(day, list.size, typical, typical - baseline)
            }
            .sortedBy { it.day }
        return if (weekdays.isEmpty()) null else TravelPattern(baseline, weekdays)
    }

    private fun recent(observations: List<TravelObservation>, now: Instant) =
        observations.filter { !it.departure.isAfter(now) && it.departure.isAfter(now.minus(LOOKBACK)) && it.travelMinutes > 0 }

    private fun minutesApart(a: LocalTime, b: LocalTime): Long {
        val diff = abs(Duration.between(a, b).toMinutes())
        return minOf(diff, MINUTES_PER_DAY - diff)
    }

    /** 짝수 개면 가운데 두 값 중 큰 값(늦게 깨우는 쪽보다 일찍 깨우는 쪽이 안전하다) */
    private fun median(values: List<Int>): Int = values.sorted().let { it[it.size / 2] }

    private const val MINUTES_PER_DAY = 24 * 60L
}
