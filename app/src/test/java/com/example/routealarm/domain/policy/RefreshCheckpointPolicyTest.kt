package com.example.routealarm.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RefreshCheckpointPolicyTest {

    private val wakeUp = Instant.parse("2026-10-10T22:15:00Z")

    @Test
    fun `알람이 멀면 하루 전에 한 번만 확인`() {
        val now = wakeUp.minus(Duration.ofDays(3))
        assertEquals(wakeUp.minus(Duration.ofHours(24)), RefreshCheckpointPolicy.nextCheckpoint(wakeUp, now))
    }

    @Test
    fun `가까워질수록 촘촘하게 확인`() {
        assertEquals(
            wakeUp.minus(Duration.ofHours(3)),
            RefreshCheckpointPolicy.nextCheckpoint(wakeUp, wakeUp.minus(Duration.ofHours(10))),
        )
        assertEquals(
            wakeUp.minus(Duration.ofMinutes(20)),
            RefreshCheckpointPolicy.nextCheckpoint(wakeUp, wakeUp.minus(Duration.ofMinutes(50))),
        )
    }

    @Test
    fun `무료 플랜은 기상 30분 전 한 번만 확인한다`() {
        val far = wakeUp.minus(Duration.ofDays(3))
        assertEquals(
            wakeUp.minus(Duration.ofMinutes(30)),
            RefreshCheckpointPolicy.nextCheckpoint(wakeUp, far, realtimeRecalculation = false),
        )
        assertNull(
            RefreshCheckpointPolicy.nextCheckpoint(wakeUp, wakeUp.minus(Duration.ofMinutes(25)), realtimeRecalculation = false),
        )
    }

    @Test
    fun `마지막 체크포인트 이후에는 더 이상 갱신하지 않는다`() {
        assertNull(RefreshCheckpointPolicy.nextCheckpoint(wakeUp, wakeUp.minus(Duration.ofMinutes(15))))
    }
}
