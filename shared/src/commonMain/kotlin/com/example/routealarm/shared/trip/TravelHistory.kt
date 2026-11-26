package com.example.routealarm.shared.trip

import com.example.routealarm.shared.TravelPatternRules
import com.example.routealarm.shared.TravelPatternSummary
import com.example.routealarm.shared.TravelSample
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** 실제 이동시간 한 건(Swift 용: epoch 밀리초) */
data class TravelRecord(val departureMillis: Long, val travelMinutes: Int)

/** 요일·시간대별 이동시간 학습을 Swift 에서 쓰기 쉽게(밀리초·분 정수, 기기 시간대) */
object TravelHistory {
    /** 같은 요일·출발 ±1시간 기록이 3번 이상이면 그 이동시간들(분), 아니면 빈 목록 */
    fun slotMinutes(records: List<TravelRecord>, departureMillis: Long, nowMillis: Long): List<Int> =
        TravelPatternRules.slotSamples(samples(records), Instant.fromEpochMilliseconds(departureMillis), Instant.fromEpochMilliseconds(nowMillis), zone())
            .map { it.inWholeMinutes.toInt() }

    /** 요일별 요약(상세 화면 "요일별 이동시간" 카드). 요일 이름은 "월"… */
    fun summary(records: List<TravelRecord>, nowMillis: Long): TravelPatternSummary? =
        TravelPatternRules.summary(samples(records), Instant.fromEpochMilliseconds(nowMillis), zone())

    fun dayName(ordinal: Int): String = listOf("월", "화", "수", "목", "금", "토", "일")[ordinal]

    private fun samples(records: List<TravelRecord>) = records.map { TravelSample(Instant.fromEpochMilliseconds(it.departureMillis), it.travelMinutes) }

    private fun zone() = TimeZone.currentSystemDefault()
}
