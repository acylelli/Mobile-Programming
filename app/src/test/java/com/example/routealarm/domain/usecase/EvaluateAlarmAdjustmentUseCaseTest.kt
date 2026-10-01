package com.example.routealarm.domain.usecase

import com.example.routealarm.domain.policy.AdjustmentDecision
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class EvaluateAlarmAdjustmentUseCaseTest {

    private val evaluate = EvaluateAlarmAdjustmentUseCase()
    private val current = Instant.parse("2026-10-05T22:15:00Z")

    private fun decide(deltaMinutes: Long, auto: Boolean = true) =
        evaluate(current, current.plusSeconds(deltaMinutes * 60), autoAdjustEnabled = auto)

    @Test
    fun `5분 미만 변동은 기존 알람 유지`() {
        assertEquals(AdjustmentDecision.Keep(-3), decide(-3))
        assertEquals(AdjustmentDecision.Keep(-4), decide(-4))
        assertEquals(AdjustmentDecision.Keep(0), decide(0))
    }

    @Test
    fun `5분 이상 앞당겨야 하면 자동 조정`() {
        assertEquals(AdjustmentDecision.AutoAdjust(-5), decide(-5))
        assertEquals(AdjustmentDecision.AutoAdjust(-17), decide(-17))
    }

    @Test
    fun `늦추는 방향은 10분 이상일 때만 반영한다`() {
        assertEquals(AdjustmentDecision.Keep(7), decide(7))
        assertEquals(AdjustmentDecision.AutoAdjust(12), decide(12))
    }

    @Test
    fun `자동 변경 최대 범위 30분 초과는 사용자 확인`() {
        assertEquals(AdjustmentDecision.AutoAdjust(-30), decide(-30))
        assertEquals(AdjustmentDecision.RequireConfirmation(-31), decide(-31))
        assertEquals(AdjustmentDecision.RequireConfirmation(45), decide(45))
    }

    @Test
    fun `자동 조정이 꺼져 있으면 추천만 한다`() {
        assertEquals(AdjustmentDecision.Suggest(-17), decide(-17, auto = false))
        assertEquals(AdjustmentDecision.Keep(-2), decide(-2, auto = false))
    }

    @Test
    fun `알람 직전이라 확인할 수 없으면 큰 앞당김도 자동 반영한다`() {
        val now = current.minusSeconds(30 * 60)
        assertEquals(
            AdjustmentDecision.AutoAdjust(-40),
            evaluate(current, current.minusSeconds(40 * 60), autoAdjustEnabled = true, now = now),
        )
        // 늦추는 방향은 예외 없이 확인 요청
        assertEquals(
            AdjustmentDecision.RequireConfirmation(40),
            evaluate(current, current.plusSeconds(40 * 60), autoAdjustEnabled = true, now = now),
        )
    }
}
