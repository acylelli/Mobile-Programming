package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/** 기상 습관 분석 결과: 평소 기상 알람을 얼마나 늦게 끄는지와 늘리면 좋을 준비 시간 */
data class WakeUpHabit(
    /** 분석에 쓴 최근 기상 횟수 */
    val samples: Int,
    /** 알람이 울린 뒤 끄기까지 걸린 시간의 중앙값(분). 다시 울림 포함 */
    val typicalLateMinutes: Int,
    /** 늘리기를 제안하는 준비 시간(분, 5분 단위) */
    val extraPreparationMinutes: Int,
)

/**
 * 알람 이력 기반 개인화: 기상 알람을 끈 시각과 예정 기상 시각의 차이를 모아
 * 평소 늦게 일어나는 습관이 있으면 준비 시간을 그만큼 늘리도록 제안한다.
 *
 * 설계 이유:
 * - 평균 대신 중앙값을 써서 한 번 크게 늦은 날(주말 늦잠 등)에 끌려가지 않는다.
 * - 최근 [LOOKBACK] 안의 기록만 보고, 제안을 적용하거나 넘긴 뒤에는 그 이후 기록만 다시 모은다.
 * - 알람을 켜 두고 자리를 비운 경우 같은 비정상 값([MAX_SAMPLE_MINUTES] 초과)은 뺀다.
 */
object WakeUpHabitPolicy {
    const val MIN_SAMPLES = 3
    const val MIN_LATE_MINUTES = 5
    const val MAX_EXTRA_MINUTES = 30
    const val MAX_SAMPLE_MINUTES = 90L
    val LOOKBACK: Duration = Duration.ofDays(30)

    fun analyze(events: List<AlarmHistoryEvent>, now: Instant, since: Instant? = null): WakeUpHabit? {
        val from = listOfNotNull(now.minus(LOOKBACK), since).max()
        val lateMinutes = events
            .asSequence()
            .filter { it.type == AlarmEventType.DISMISSED && it.occurredAt.isAfter(from) }
            .mapNotNull { event ->
                val planned = event.newWakeUp ?: return@mapNotNull null
                Duration.between(planned, event.occurredAt).toMinutes()
            }
            .filter { it in 0..MAX_SAMPLE_MINUTES }
            .sorted()
            .toList()
        if (lateMinutes.size < MIN_SAMPLES) return null
        val median = lateMinutes[lateMinutes.size / 2].toInt()
        if (median < MIN_LATE_MINUTES) return null
        val extra = (ceil(median / STEP) * STEP).toInt().coerceAtMost(MAX_EXTRA_MINUTES)
        return WakeUpHabit(samples = lateMinutes.size, typicalLateMinutes = median, extraPreparationMinutes = extra)
    }

    private const val STEP = 5.0
}
