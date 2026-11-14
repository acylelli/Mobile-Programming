package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume

/**
 * 휠의 움직임(끌기, 놓은 뒤의 관성, 잡아서 멈추기, 값에 맞춰 옮기기)을 프레임 단위로 검증한다.
 * 애니메이션은 [ManualFrameClock] 의 프레임을 기다리므로 테스트가 원하는 시점에 끼어들 수 있다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WheelStateTest {

    /* ───────────── 끌기 ───────────── */

    @Test
    fun `손가락을 올리면 다음 칸이 내리면 이전 칸이 가운데로 온다`() {
        val wheel = WheelState(10, ROW_PX)

        wheel.draggableState.dispatchRawDelta(-ROW_PX)
        assertEquals(11f, wheel.position, TOLERANCE)
        assertEquals(11, wheel.centeredIndex)

        wheel.draggableState.dispatchRawDelta(ROW_PX * 2.25f)
        assertEquals(8.75f, wheel.position, TOLERANCE)
        assertEquals(9, wheel.centeredIndex)
    }

    @Test
    fun `끝이 있는 휠은 끝을 넘겨 끌어도 반 칸을 넘지 않고 값은 끝에 머문다`() {
        val wheel = WheelState(0, ROW_PX, bounds = 0..1)

        wheel.draggableState.dispatchRawDelta(ROW_PX * 5)
        assertTrue(wheel.position < 0f && wheel.position > -0.5f)
        assertEquals(0, wheel.centeredIndex)

        wheel.draggableState.dispatchRawDelta(-ROW_PX * 20)
        assertTrue(wheel.position > 1f && wheel.position < 1.5f)
        assertEquals(1, wheel.centeredIndex)
    }

    /* ───────────── 손을 뗀 뒤 ───────────── */

    @Test
    fun `튕기면 멈출 칸이 구르기 전에 정해지고 정확히 그 칸에서 멈춘다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(0, ROW_PX)
        var reported: Int? = null

        wheel.onDragStarted()
        val settle = launch(clock) { wheel.settle(velocityPx = -ROW_PX * 20) { reported = wheel.targetIndex } }
        runCurrent()

        val target = wheel.targetIndex
        assertEquals(target, reported)
        assertTrue("초당 20칸이면 여러 칸을 굴러간다: $target", target in 8..11)
        assertEquals(0f, wheel.position, TOLERANCE)
        assertTrue(wheel.isUserActive)
        assertTrue(wheel.isSpinning)
        assertFalse(wheel.isHeld)

        finish(clock)

        assertTrue(settle.isCompleted && !settle.isCancelled)
        assertEquals(target.toFloat(), wheel.position, 0f)
        assertEquals(target, wheel.centeredIndex)
        assertEquals(target, wheel.targetIndex)
        assertFalse(wheel.isUserActive)
        assertFalse(wheel.isSpinning)
    }

    @Test
    fun `관성으로 구르는 동안 뒤로 가거나 멈출 칸을 지나치지 않는다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(3, ROW_PX)

        wheel.onDragStarted()
        wheel.draggableState.dispatchRawDelta(ROW_PX * 0.4f)
        launch(clock) { wheel.settle(velocityPx = ROW_PX * 14) {} }
        runCurrent()

        val target = wheel.targetIndex
        var previous = wheel.position
        while (clock.hasWaiters) {
            frames(clock, 1)
            assertTrue(wheel.position <= previous + TOLERANCE)
            assertTrue(wheel.position >= target - TOLERANCE)
            previous = wheel.position
        }
        assertEquals(target.toFloat(), wheel.position, 0f)
    }

    @Test
    fun `거의 멈춘 채 놓으면 가장 가까운 칸에 붙는다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(3, ROW_PX)

        wheel.onDragStarted()
        wheel.draggableState.dispatchRawDelta(-ROW_PX * 0.3f)
        launch(clock) { wheel.settle(velocityPx = 0f) {} }
        runCurrent()

        assertEquals(3, wheel.targetIndex)
        assertFalse(wheel.isSpinning)
        finish(clock)
        assertEquals(3f, wheel.position, 0f)
    }

    @Test
    fun `끝이 있는 휠은 세게 튕겨도 끝 칸에서 멈춘다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(0, ROW_PX, bounds = 0..1)

        wheel.onDragStarted()
        launch(clock) { wheel.settle(velocityPx = -ROW_PX * 40) {} }
        runCurrent()

        assertEquals(1, wheel.targetIndex)
        finish(clock)
        assertEquals(1f, wheel.position, 0f)
    }

    /* ───────────── 끼어들기 ───────────── */

    @Test
    fun `도는 휠을 잡으면 그 자리에 멈추고 정해 둔 목표가 사라진다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(0, ROW_PX)

        wheel.onDragStarted()
        val settle = launch(clock) { wheel.settle(velocityPx = -ROW_PX * 30) {} }
        runCurrent()
        frames(clock, 10)
        val caughtAt = wheel.position
        assertTrue(caughtAt > 0f)

        // 손가락이 닿는다: draggable 이 하는 것과 같은 순서로 알리고 끌기를 시작한다.
        wheel.onDragStarted()
        val drag = launch(clock) { wheel.draggableState.drag(MutatePriority.UserInput) { awaitCancellation() } }
        runCurrent()
        frames(clock, 5)

        assertTrue(settle.isCancelled)
        assertEquals(caughtAt, wheel.position, TOLERANCE)
        assertTrue(wheel.isHeld)
        assertTrue(wheel.isUserActive)
        assertFalse(wheel.isSpinning)
        assertEquals(wheel.centeredIndex, wheel.targetIndex)
        drag.cancel()
    }

    @Test
    fun `손가락으로 잡고 있는 휠은 값에 맞춰 옮기지 않는다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(4, ROW_PX)

        wheel.onDragStarted()
        var announced = false
        val sync = launch(clock) { wheel.animateTo(9, byUser = false) { announced = true } }
        runCurrent()

        assertTrue(sync.isCancelled)
        assertFalse(announced)
        assertEquals(4f, wheel.position, TOLERANCE)
        assertEquals(4, wheel.targetIndex)
    }

    @Test
    fun `바깥 값에 맞춰 옮기면 관성으로 돌던 휠도 멈추고 그 칸으로 간다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(0, ROW_PX)
        var rested = false

        wheel.onDragStarted()
        val settle = launch(clock) {
            wheel.settle(velocityPx = -ROW_PX * 20) {}
            rested = true
        }
        runCurrent()
        frames(clock, 3)

        launch(clock) { wheel.animateTo(2, byUser = false) }
        runCurrent()

        assertTrue(settle.isCancelled)
        assertEquals(2, wheel.targetIndex)
        assertFalse(wheel.isUserActive)
        assertFalse(wheel.isSpinning)

        finish(clock)
        assertEquals(2f, wheel.position, 0f)
        // 밀려난 움직임은 "멈췄다"고 알리지 않는다.
        assertFalse(rested)
    }

    @Test
    fun `칸을 골라 돌리면 목표가 바로 정해지고 그 칸에서 멈춘다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(5, ROW_PX)
        var picked: Int? = null

        launch(clock) { wheel.animateTo(8, byUser = true) { picked = wheel.targetIndex } }
        runCurrent()

        assertEquals(8, picked)
        assertEquals(5, wheel.centeredIndex)
        assertTrue(wheel.isUserActive)
        assertFalse(wheel.isSpinning)

        finish(clock)
        assertEquals(8f, wheel.position, 0f)
        assertFalse(wheel.isUserActive)
    }

    @Test
    fun `바로 옮기면 애니메이션 없이 그 칸에 놓인다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(5, ROW_PX)

        launch(clock) { wheel.snapTo(-7) }
        runCurrent()

        assertFalse(clock.hasWaiters)
        assertEquals(-7f, wheel.position, 0f)
        assertEquals(-7, wheel.targetIndex)
        assertFalse(wheel.isUserActive)
    }

    @Test
    fun `움직임이 중간에 취소되면 칸 사이에 걸쳐 있음을 알 수 있다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(0, ROW_PX, bounds = 0..1)
        assertFalse(wheel.isBetweenRows)

        // 오전/오후 휠이 시 휠을 따라 넘어가다가, 따라가던 쪽이 취소되는 경우
        val follow = launch(clock) { wheel.animateTo(1, byUser = false) }
        runCurrent()
        assertFalse("목표를 향해 가는 중에는 제자리로 본다", wheel.isBetweenRows)
        while (wheel.position < 0.55f) frames(clock, 1)
        follow.cancel()
        runCurrent()

        assertEquals(1, wheel.targetIndex)
        assertTrue(wheel.position < 1f)
        assertTrue(wheel.isBetweenRows)
        assertFalse(wheel.isUserActive)

        // 다시 옮기면 걸쳐 있던 자리에서 이어서 칸에 맞는다.
        launch(clock) { wheel.animateTo(1, byUser = false) }
        runCurrent()
        finish(clock)
        assertEquals(1f, wheel.position, 0f)
        assertFalse(wheel.isBetweenRows)
    }

    @Test
    fun `손가락으로 잡고 있는 동안은 칸 사이에 있어도 걸쳐 멈춘 것으로 보지 않는다`() {
        val wheel = WheelState(3, ROW_PX)

        wheel.onDragStarted()
        wheel.draggableState.dispatchRawDelta(-ROW_PX * 0.4f)

        assertTrue(wheel.isHeld)
        assertFalse(wheel.isBetweenRows)
    }

    /* ───────────── 오전/오후 전환(반 바퀴 옮기기) ───────────── */

    @Test
    fun `멈춰 있는 휠을 반 바퀴 옮기면 위치만 그만큼 바뀐다`() {
        val wheel = WheelState(3, ROW_PX)

        wheel.jumpBy(12)

        assertEquals(15f, wheel.position, 0f)
        assertEquals(15, wheel.targetIndex)
    }

    @Test
    fun `도는 도중에 반 바퀴 옮겨도 움직임이 이어지고 그만큼 옮겨진 칸에서 멈춘다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(0, ROW_PX)

        wheel.onDragStarted()
        launch(clock) { wheel.settle(velocityPx = -ROW_PX * 20) {} }
        runCurrent()
        val target = wheel.targetIndex
        frames(clock, 5)
        val before = wheel.position

        wheel.jumpBy(12)
        assertEquals(before + 12f, wheel.position, TOLERANCE)
        assertEquals(target + 12, wheel.targetIndex)
        assertTrue(wheel.isSpinning)

        frames(clock, 1)
        assertTrue(wheel.position > before + 12f)

        finish(clock)
        assertEquals((target + 12).toFloat(), wheel.position, 0f)
    }

    @Test
    fun `앞선 움직임이 물러나기를 기다리는 사이에 반 바퀴 옮겨도 그만큼 옮겨진 칸에서 멈춘다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(0, ROW_PX)

        // 관성으로 도는 중에 다른 칸을 고른다: 새 움직임은 앞선 움직임이 정리될 때까지 잠깐 기다린다.
        wheel.onDragStarted()
        launch(clock) { wheel.settle(velocityPx = -ROW_PX * 20) {} }
        runCurrent()
        frames(clock, 3)
        var jumped = false
        launch(clock) {
            wheel.animateTo(5, byUser = true) {
                wheel.jumpBy(12)
                jumped = true
            }
        }
        runCurrent()

        assertTrue(jumped)
        assertEquals(17, wheel.targetIndex)
        finish(clock)
        assertEquals(17f, wheel.position, 0f)
    }

    @Test
    fun `움직임을 시작한 직후 반 바퀴 옮겨도 목표와 멈춘 자리가 어긋나지 않는다`() = runTest {
        val clock = ManualFrameClock()
        val wheel = WheelState(2, ROW_PX)

        launch(clock) { wheel.animateTo(4, byUser = true) { wheel.jumpBy(-12) } }
        runCurrent()

        assertEquals(-8, wheel.targetIndex)
        finish(clock)
        assertEquals(-8f, wheel.position, 0f)
    }

    private fun TestScope.frames(clock: ManualFrameClock, count: Int) {
        repeat(count) {
            clock.advance()
            runCurrent()
        }
    }

    private fun TestScope.finish(clock: ManualFrameClock) {
        var frames = 0
        while (clock.hasWaiters) {
            check(frames++ < MAX_FRAMES) { "animation did not finish" }
            clock.advance()
            runCurrent()
        }
    }

    /** 테스트가 한 프레임씩 넘기는 시계. 애니메이션은 다음 프레임을 기다리며 멈춰 있다가 [advance] 때만 진행한다. */
    private class ManualFrameClock : MonotonicFrameClock {
        private var nanos = 0L
        private val waiting = mutableListOf<CancellableContinuation<Long>>()

        val hasWaiters: Boolean get() = waiting.any { it.isActive }

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            val time = suspendCancellableCoroutine { waiting += it }
            return onFrame(time)
        }

        fun advance() {
            nanos += FRAME_NANOS
            val ready = waiting.toList()
            waiting.clear()
            ready.forEach { if (it.isActive) it.resume(nanos) }
        }
    }

    private companion object {
        const val ROW_PX = 96f
        const val TOLERANCE = 1e-3f
        const val FRAME_NANOS = 16_000_000L
        const val MAX_FRAMES = 2_000
    }
}
