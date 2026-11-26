package com.example.routealarm.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AlarmEngineTest {
    private val target = Instant.parse("2026-10-06T00:00:00Z") // 09:00 KST
    private val now = Instant.parse("2026-10-05T20:00:00Z")

    private fun input(
        travel: Int = 60,
        delay: Int = 0,
        preparation: Int = 30,
        buffer: Int = 10,
        realtime: Boolean = true,
        weather: Int = 0,
    ) = EngineInput(
        targetArrival = target,
        currentTravel = travel.minutes,
        delay = delay.minutes,
        preparation = preparation.minutes,
        baseBufferMinutes = buffer,
        isRealtime = realtime,
        weatherExtraMinutes = weather,
    )

    @Test
    fun 출발은_목표에서_이동과_여유를_빼고_기상은_준비만큼_더_이르다() {
        val result = assertIs<EngineResult.OnTime>(AlarmEngine.plan(input(), now))
        assertEquals(Instant.parse("2026-10-05T22:50:00Z"), result.times.departure)
        assertEquals(Instant.parse("2026-10-05T23:50:00Z"), result.times.estimatedArrival)
        assertEquals(Instant.parse("2026-10-05T22:20:00Z"), result.times.wakeUp)
        assertEquals(false, result.wakeUpAlreadyPassed)
    }

    @Test
    fun 비_예보면_여유가_5분_늘어_5분_일찍_깨운다() {
        val dry = assertIs<EngineResult.OnTime>(AlarmEngine.plan(input(), now))
        val rain = assertIs<EngineResult.OnTime>(AlarmEngine.plan(input(weather = WeatherRules.extraMinutes(Precipitation.RAIN)), now))
        assertEquals(dry.times.wakeUp - 5.minutes, rain.times.wakeUp)
        assertEquals(15, rain.times.safetyBufferMinutes)
    }

    @Test
    fun 지연과_캐시_데이터는_여유를_더한다() {
        // 지연 20분 → 6분, 실시간 아님 → 5분
        assertEquals(21.minutes, AlarmEngine.safetyBuffer(10, 20.minutes, isRealtime = false))
        // 지연 여유는 10분까지
        assertEquals(20.minutes, AlarmEngine.safetyBuffer(10, 90.minutes, isRealtime = true))
    }

    @Test
    fun 이동시간은_과거_평균과_섞되_현재보다_짧아지지_않고_분_단위로_올린다() {
        assertEquals(69.minutes, AlarmEngine.estimateTravel(60.minutes, listOf(90.minutes)))
        assertEquals(70.minutes, AlarmEngine.estimateTravel(70.minutes, listOf(40.minutes)))
        assertEquals(61.minutes, AlarmEngine.estimateTravel(60.minutes + 1.seconds))
    }

    @Test
    fun 지금_출발해도_늦으면_TooLate() {
        val lateNow = Instant.parse("2026-10-05T23:30:00Z")
        val result = assertIs<EngineResult.TooLate>(AlarmEngine.plan(input(), lateNow))
        assertEquals(30, result.lateByMinutes)
        assertEquals(lateNow, result.times.wakeUp)
    }

    @Test
    fun 과거_목표와_음수_시간은_계산하지_않는다() {
        assertEquals(EngineResult.Invalid(InvalidReason.TARGET_IN_PAST), AlarmEngine.plan(input(), target))
        assertEquals(EngineResult.Invalid(InvalidReason.NEGATIVE_DURATION), AlarmEngine.plan(input(travel = -1), now))
    }

    @Test
    fun 조정은_흔들림을_무시하고_늦추기는_보수적으로_한다() {
        val wake = Instant.parse("2026-10-05T22:00:00Z")
        assertIs<AdjustmentDecision.Keep>(AdjustmentEngine.evaluate(wake, wake - 3.minutes, true))
        assertIs<AdjustmentDecision.AutoAdjust>(AdjustmentEngine.evaluate(wake, wake - 6.minutes, true))
        assertIs<AdjustmentDecision.Keep>(AdjustmentEngine.evaluate(wake, wake + 8.minutes, true))
        assertIs<AdjustmentDecision.Suggest>(AdjustmentEngine.evaluate(wake, wake - 6.minutes, false))
        assertIs<AdjustmentDecision.RequireConfirmation>(AdjustmentEngine.evaluate(wake, wake - 40.minutes, true, now = now))
        // 알람까지 1시간이 안 남았으면 크게 앞당기는 것도 자동으로
        assertTrue(AdjustmentEngine.evaluate(wake, wake - 40.minutes, true, now = wake - 30.minutes) is AdjustmentDecision.AutoAdjust)
    }
}
