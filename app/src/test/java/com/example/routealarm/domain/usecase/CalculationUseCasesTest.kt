package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CalculateSafetyBufferUseCaseTest {
    private val calculate = CalculateSafetyBufferUseCase()

    @Test
    fun `지연이 없으면 기본 여유시간 그대로`() {
        assertEquals(Duration.ofMinutes(10), calculate(10, Duration.ZERO, isRealtime = true))
    }

    @Test
    fun `지연의 30퍼센트를 올림해 추가한다`() {
        assertEquals(Duration.ofMinutes(13), calculate(10, Duration.ofMinutes(7), isRealtime = true))
    }

    @Test
    fun `지연 추가 여유는 최대 10분`() {
        assertEquals(Duration.ofMinutes(20), calculate(10, Duration.ofMinutes(90), isRealtime = true))
    }

    @Test
    fun `음수 기본값과 음수 지연은 0으로 본다`() {
        assertEquals(Duration.ZERO, calculate(-5, Duration.ofMinutes(-3), isRealtime = true))
    }
}

class EstimateTravelTimeUseCaseTest {
    private val estimate = EstimateTravelTimeUseCase()

    @Test
    fun `이력이 없으면 현재 ETA 를 분 단위로 올림`() {
        assertEquals(Duration.ofMinutes(61), estimate(Duration.ofSeconds(60 * 60 + 10)))
    }

    @Test
    fun `평균이 더 길면 가중 평균만큼 늘린다`() {
        // 0.7 * 60 + 0.3 * 80 = 66
        val result = estimate(Duration.ofMinutes(60), listOf(Duration.ofMinutes(80), Duration.ofMinutes(80)))
        assertEquals(Duration.ofMinutes(66), result)
    }

    @Test
    fun `평균이 더 짧아도 현재 ETA 보다 짧아지지 않는다`() {
        val result = estimate(Duration.ofMinutes(70), listOf(Duration.ofMinutes(40)))
        assertEquals(Duration.ofMinutes(70), result)
    }
}

class CalculateDepartureAndWakeUpTest {
    private val departure = CalculateDepartureTimeUseCase()
    private val wakeUp = CalculateWakeUpTimeUseCase()
    private val target = Instant.parse("2026-10-06T00:00:00Z") // 09:00 KST

    @Test
    fun `출발 = 목표 - 이동 - 여유, 기상 = 출발 - 준비`() {
        val times = (departure(target, Duration.ofMinutes(60), Duration.ofMinutes(10)) as AppResult.Success).data
        assertEquals(Instant.parse("2026-10-05T22:50:00Z"), times.recommendedDeparture)
        assertEquals(Instant.parse("2026-10-05T23:50:00Z"), times.estimatedArrival)

        val wake = (wakeUp(times.recommendedDeparture, Duration.ofMinutes(30)) as AppResult.Success).data
        assertEquals(Instant.parse("2026-10-05T22:20:00Z"), wake)
    }

    @Test
    fun `음수 이동시간은 실패`() {
        assertTrue(departure(target, Duration.ofMinutes(-1), Duration.ZERO) is AppResult.Failure)
        assertTrue(wakeUp(target, Duration.ofMinutes(-1)) is AppResult.Failure)
    }
}
