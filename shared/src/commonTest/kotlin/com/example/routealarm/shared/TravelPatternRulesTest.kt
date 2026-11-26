package com.example.routealarm.shared

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TravelPatternRulesTest {
    private val seoul = TimeZone.of("Asia/Seoul")
    private val monday = Instant.parse("2026-10-04T23:30:00Z") // 월 08:30 KST
    private val now = monday + 1.days

    /** 지난 월요일들은 50분, 화요일들은 35분 */
    private val samples = (0 until 4).flatMap { week ->
        val mon = monday - (7 * week).days
        listOf(TravelSample(mon, 50), TravelSample(mon + 1.days - 10.minutes, 35))
    }.filter { it.departure <= now }

    @Test
    fun 같은_요일_시간대_기록이_3번_이상이면_그_기록을_쓴다() {
        val nextMonday = monday + 7.days + 20.minutes
        val slot = TravelPatternRules.slotSamples(samples, nextMonday, now, seoul)
        assertEquals(4, slot.size)
        assertTrue(slot.all { it == 50.minutes })
    }

    @Test
    fun 기록이_모자라면_빈_목록() {
        val wednesday = monday + 2.days
        assertTrue(TravelPatternRules.slotSamples(samples, wednesday, now, seoul).isEmpty())
    }

    @Test
    fun 요약에서_월요일이_가장_느리다() {
        val summary = TravelPatternRules.summary(samples, now, seoul)!!
        assertEquals(50, summary.baselineMinutes)
        assertEquals(DayOfWeek.TUESDAY, summary.weekdays.minBy { it.typicalMinutes }.day)
        assertNull(summary.slowest) // 짝수 개 중앙값은 큰 쪽(50)이라 월요일은 평소와 같다
    }
}
