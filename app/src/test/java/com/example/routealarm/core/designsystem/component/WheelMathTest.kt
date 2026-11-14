package com.example.routealarm.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

class WheelMathTest {

    /* ───────────── 고리 휠의 칸 번호 ───────────── */

    @Test
    fun `칸 번호는 음수이거나 한 바퀴를 넘어도 값으로 바뀐다`() {
        assertEquals(59, WheelRing.slot(-1, 60))
        assertEquals(1, WheelRing.slot(61, 60))
        assertEquals(0, WheelRing.slot(24, 24))
        assertEquals(23, WheelRing.slot(-25, 24))
    }

    @Test
    fun `가장 가까운 칸은 짧은 쪽으로 돌아서 찾는다`() {
        assertEquals(62, WheelRing.nearestIndex(from = 58, targetSlot = 2, size = 60))
        assertEquals(-2, WheelRing.nearestIndex(from = 2, targetSlot = 58, size = 60))
        assertEquals(125, WheelRing.nearestIndex(from = 130, targetSlot = 5, size = 60))
        assertEquals(7, WheelRing.nearestIndex(from = 7, targetSlot = 7, size = 60))
    }

    @Test
    fun `정확히 반 바퀴 차이면 값이 커지는 쪽으로 돈다`() {
        assertEquals(30, WheelRing.nearestIndex(from = 0, targetSlot = 30, size = 60))
        assertEquals(15, WheelRing.nearestIndex(from = 3, targetSlot = 15, size = 24))
    }

    /* ───────────── 시각 휠 ───────────── */

    @Test
    fun `시 휠 숫자는 12 다음이 1 이다`() {
        assertEquals(listOf(12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 1), (0..13).map(WheelTimeMath::hourLabel))
        assertEquals(11, WheelTimeMath.hourLabel(-1))
    }

    @Test
    fun `11 과 12 사이를 지나면 오전 오후가 바뀌고 12 와 1 사이에서는 그대로다`() {
        assertFalse(WheelTimeMath.isPm(11))
        assertTrue(WheelTimeMath.isPm(12))
        assertTrue(WheelTimeMath.isPm(13))
        assertTrue(WheelTimeMath.isPm(23))
        assertFalse(WheelTimeMath.isPm(24))
        assertFalse(WheelTimeMath.isPm(25))
        assertTrue(WheelTimeMath.isPm(-1))
    }

    @Test
    fun `여러 바퀴를 돌려도 숫자와 오전 오후가 가리키는 시각이 어긋나지 않는다`() {
        for (index in -100..100) {
            val hour = WheelTimeMath.time(index, 0).hour
            val fromLabel = WheelTimeMath.hourLabel(index) % 12 + if (WheelTimeMath.isPm(index)) 12 else 0
            assertEquals("칸 $index", hour, fromLabel)
        }
    }

    @Test
    fun `자정은 오전 12시 정오는 오후 12시다`() {
        assertEquals(LocalTime.MIDNIGHT, WheelTimeMath.time(0, 0))
        assertEquals(12, WheelTimeMath.hourLabel(0))
        assertFalse(WheelTimeMath.isPm(0))
        assertEquals(LocalTime.NOON, WheelTimeMath.time(12, 0))
        assertEquals(12, WheelTimeMath.hourLabel(12))
        assertTrue(WheelTimeMath.isPm(12))
    }

    @Test
    fun `분 휠은 59 에서 0 으로 넘어가도 시를 바꾸지 않는다`() {
        assertEquals(LocalTime.of(9, 59), WheelTimeMath.time(9, 59))
        assertEquals(LocalTime.of(9, 0), WheelTimeMath.time(9, 60))
        assertEquals(LocalTime.of(9, 59), WheelTimeMath.time(9, -1))
    }

    @Test
    fun `오전 오후만 바꾸면 보이는 숫자는 그대로다`() {
        assertEquals(15, WheelTimeMath.withPeriod(3, pm = true))
        assertEquals(3, WheelTimeMath.withPeriod(15, pm = false))
        assertEquals(15, WheelTimeMath.withPeriod(15, pm = true))
        for (index in -30..30) {
            for (pm in listOf(true, false)) {
                val moved = WheelTimeMath.withPeriod(index, pm)
                assertEquals(pm, WheelTimeMath.isPm(moved))
                assertEquals(WheelTimeMath.hourLabel(index), WheelTimeMath.hourLabel(moved))
            }
        }
    }

    /* ───────────── 길이 휠 ───────────── */

    @Test
    fun `최대 2시간에 5분 단위면 시간 휠은 3칸 분 휠은 12칸이다`() {
        assertEquals(3, WheelDurationMath.hourCount(120))
        assertEquals(12, WheelDurationMath.minuteSlots(5))
    }

    @Test
    fun `최대가 정각이 아니어도 그 값이 들어가는 시간까지만 칸을 둔다`() {
        assertEquals(1, WheelDurationMath.hourCount(55))
        assertEquals(2, WheelDurationMath.hourCount(60))
        assertEquals(2, WheelDurationMath.hourCount(90))
        assertEquals(2, WheelDurationMath.hourCount(119))
    }

    @Test
    fun `분 단위 값과 휠 칸이 서로 바뀐다`() {
        assertEquals(1, WheelDurationMath.hourIndex(95))
        assertEquals(7, WheelDurationMath.minuteSlot(95, 5))
        assertEquals(95, WheelDurationMath.totalMinutes(hourIndex = 1, minuteIndex = 7, step = 5))
        for (total in 0..175 step 5) {
            val roundTrip = WheelDurationMath.totalMinutes(WheelDurationMath.hourIndex(total), WheelDurationMath.minuteSlot(total, 5), 5)
            assertEquals(total, roundTrip)
        }
    }

    @Test
    fun `분 휠은 55 다음이 0 이고 시간을 바꾸지 않는다`() {
        assertEquals(0, WheelDurationMath.minuteLabel(12, 5))
        assertEquals(55, WheelDurationMath.minuteLabel(-1, 5))
        assertEquals(65, WheelDurationMath.totalMinutes(hourIndex = 1, minuteIndex = 13, step = 5))
    }

    @Test
    fun `단위에 맞지 않는 값은 아래 칸으로 내린다`() {
        assertEquals(7, WheelDurationMath.minuteSlot(37, 5))
        assertEquals(35, WheelDurationMath.minuteLabel(WheelDurationMath.minuteSlot(37, 5), 5))
    }

    /* ───────────── 내가 알린 값과 바깥에서 온 값 ───────────── */

    @Test
    fun `처음 받은 값과 같은 값이 다시 들어오면 새 값이 아니다`() {
        val echo = EchoFilter(30)
        assertFalse(echo.isNew(30))
    }

    @Test
    fun `알린 값이 돌아오면 메아리이고 알린 적 없는 값은 새 값이다`() {
        val echo = EchoFilter(30)

        echo.reported(50)
        assertFalse(echo.isNew(50))

        assertTrue(echo.isNew(45))
        assertTrue(echo.isNew(30))
    }

    @Test
    fun `연달아 알린 값이 늦게 돌아와도 메아리로 본다`() {
        val echo = EchoFilter(540)

        echo.reported(840)
        echo.reported(865)
        // 첫 번째 값의 메아리가 두 번째 값을 알린 뒤에 도착해도 바깥에서 온 값으로 착각하지 않는다.
        assertFalse(echo.isNew(840))
        assertFalse(echo.isNew(865))
    }

    @Test
    fun `중간 값을 건너뛰고 마지막 값만 돌아와도 메아리로 본다`() {
        val echo = EchoFilter(30)

        echo.reported(20)
        echo.reported(50)
        assertFalse(echo.isNew(50))
        // 건너뛰어진 값은 더 기다리지 않는다: 나중에 같은 값이 들어오면 새 값이다.
        assertTrue(echo.isNew(20))
    }

    @Test
    fun `바깥에서 새 값이 오면 그전에 알린 값은 더 기다리지 않는다`() {
        val echo = EchoFilter(30)

        echo.reported(50)
        assertTrue(echo.isNew(45))
        assertTrue(echo.isNew(50))
    }

    /* ───────────── 손을 뗀 뒤의 움직임 ───────────── */

    @Test
    fun `거의 멈춘 채 놓으면 가장 가까운 칸에 스프링으로 붙는다`() {
        val plan = WheelMotion.planSettle(position = 3.4f, glideRows = 0.2f, bounds = null)
        assertEquals(4, plan.target)
        assertNull(plan.glideScale)
    }

    @Test
    fun `튕기면 관성 거리를 칸에 맞게 조금만 늘이거나 줄인다`() {
        val forward = WheelMotion.planSettle(position = 3.2f, glideRows = 10.5f, bounds = null)
        assertEquals(14, forward.target)
        assertEquals((14 - 3.2f) / 10.5f, forward.glideScale!!, 1e-4f)

        val backward = WheelMotion.planSettle(position = 0f, glideRows = -7.3f, bounds = null)
        assertEquals(-7, backward.target)
        assertEquals(7f / 7.3f, backward.glideScale!!, 1e-4f)
    }

    @Test
    fun `고리 휠은 한 칸 넘게 굴러가면 언제나 관성으로 가고 방향과 속도가 튀지 않는다`() {
        for (tenth in 0..10) {
            val position = tenth / 10f
            for (glide in listOf(1f, 1.4f, 2f, 5.3f, 31f, -1f, -1.6f, -3f, -12.5f)) {
                val plan = WheelMotion.planSettle(position, glide, bounds = null)
                assertEquals((position + glide).roundToInt(), plan.target)
                val scale = plan.glideScale
                assertNotNull("위치 $position 관성 $glide", scale)
                // 멈출 칸은 자연히 멈출 자리에서 반 칸 이내이므로 거리 조정도 그만큼뿐이다.
                assertTrue("비율 $scale", abs(scale!! - 1f) <= 0.5f / abs(glide) + 1e-4f)
                assertEquals(sign(glide), sign(plan.target - position))
            }
        }
    }

    @Test
    fun `끝이 있는 휠은 세게 튕겨도 범위를 넘지 않고 스프링으로 끝에 붙는다`() {
        val plan = WheelMotion.planSettle(position = 0.2f, glideRows = 6f, bounds = 0..1)
        assertEquals(1, plan.target)
        assertNull(plan.glideScale)

        val beyondStart = WheelMotion.planSettle(position = -0.4f, glideRows = -3f, bounds = 0..1)
        assertEquals(0, beyondStart.target)
        assertNull(beyondStart.glideScale)
    }

    @Test
    fun `끝이 있는 휠도 범위 안에서는 관성으로 굴러간다`() {
        val plan = WheelMotion.planSettle(position = 0f, glideRows = 1.9f, bounds = 0..2)
        assertEquals(2, plan.target)
        assertNotNull(plan.glideScale)
    }

    /* ───────────── 원통 모양 ───────────── */

    @Test
    fun `가운데 칸은 제자리에 원래 크기로 또렷하게 보인다`() {
        assertEquals(0f, WheelGeometry.offsetY(0f, RADIUS), TOLERANCE)
        assertEquals(1f, WheelGeometry.scaleX(0f), TOLERANCE)
        assertEquals(1f, WheelGeometry.scaleY(0f), TOLERANCE)
        assertEquals(1f, WheelGeometry.alpha(0f), TOLERANCE)
        assertEquals(1f, WheelGeometry.focus(0f), TOLERANCE)
    }

    @Test
    fun `칸 간격은 가운데에서 32 로 시작해 옆면으로 갈수록 좁아진다`() {
        assertEquals(32f, RADIUS * WheelGeometry.ANGLE_PER_ROW, TOLERANCE)
        // 아이폰 휠에서 잰 칸 위치(31.5, 58.5, 77.7, 87.3)와 반 포인트 이내로 맞는다.
        val offsets = (1..4).map { WheelGeometry.offsetY(it.toFloat(), RADIUS) }
        assertEquals(31.5f, offsets[0], 0.5f)
        assertEquals(58.5f, offsets[1], 0.5f)
        assertEquals(77.7f, offsets[2], 0.5f)
        assertEquals(87.3f, offsets[3], 0.5f)
        val gaps = (listOf(0f) + offsets).zipWithNext { a, b -> b - a }
        assertEquals(gaps.sortedDescending(), gaps)
    }

    @Test
    fun `선택 띠 밖의 칸은 작고 흐리게 보인다`() {
        assertEquals(0f, WheelGeometry.focus(1f), TOLERANCE)
        assertEquals(0.869f, WheelGeometry.scaleX(1f), 0.005f)
        assertEquals(0.818f, WheelGeometry.scaleY(1f), 0.005f)
        assertEquals(0.4f, WheelGeometry.alpha(1f), TOLERANCE)
        assertEquals(0.4f * 0.92f, WheelGeometry.alpha(2f), TOLERANCE)
        assertEquals(0.4f * 0.55f, WheelGeometry.alpha(3f), TOLERANCE)
        assertEquals(0.4f * 0.25f, WheelGeometry.alpha(4f), TOLERANCE)
        assertTrue(WheelGeometry.scaleY(3f) < WheelGeometry.scaleY(2f))
    }

    @Test
    fun `밝은 바탕에서는 선택 띠 밖의 칸을 조금 더 진하게 그린다`() {
        assertEquals(0.5f, WheelGeometry.alpha(1f, WheelGeometry.OUTER_ALPHA_ON_LIGHT), TOLERANCE)
        assertEquals(0.5f * 0.55f, WheelGeometry.alpha(3f, WheelGeometry.OUTER_ALPHA_ON_LIGHT), TOLERANCE)
        // 가운데 칸은 바탕과 관계없이 또렷하다.
        assertEquals(1f, WheelGeometry.alpha(0f, WheelGeometry.OUTER_ALPHA_ON_LIGHT), TOLERANCE)
    }

    @Test
    fun `선택 띠를 드나드는 칸은 크기와 진하기가 끊기지 않고 이어진다`() {
        val measures = listOf<(Float) -> Float>({ WheelGeometry.alpha(it) }, WheelGeometry::scaleX, WheelGeometry::scaleY)
        for (measure in measures) {
            var previous = measure(0f)
            for (hundredth in 1..100) {
                val value = measure(hundredth / 100f)
                // 가운데에서 멀어질수록 줄어들기만 하고, 0.01칸 사이에 눈에 띄게 뛰지 않는다.
                assertTrue(value <= previous + TOLERANCE)
                assertTrue(previous - value < 0.03f)
                previous = value
            }
        }
    }

    @Test
    fun `옆면을 넘어간 칸은 보이지 않고 휠 밖으로 나가지 않는다`() {
        assertEquals(0f, WheelGeometry.alpha(4.5f), TOLERANCE)
        assertEquals(0f, WheelGeometry.alpha(7f), TOLERANCE)
        assertEquals(0f, WheelGeometry.scaleY(5f), TOLERANCE)
        assertEquals(RADIUS, WheelGeometry.offsetY(6f, RADIUS), TOLERANCE)
        assertEquals(-RADIUS, WheelGeometry.offsetY(-6f, RADIUS), TOLERANCE)
    }

    @Test
    fun `위와 아래는 대칭이다`() {
        for (rows in listOf(0.3f, 1f, 2.5f, 4f)) {
            assertEquals(-WheelGeometry.offsetY(rows, RADIUS), WheelGeometry.offsetY(-rows, RADIUS), TOLERANCE)
            assertEquals(WheelGeometry.scaleX(rows), WheelGeometry.scaleX(-rows), TOLERANCE)
            assertEquals(WheelGeometry.scaleY(rows), WheelGeometry.scaleY(-rows), TOLERANCE)
            assertEquals(WheelGeometry.alpha(rows), WheelGeometry.alpha(-rows), TOLERANCE)
        }
    }

    @Test
    fun `누른 높이에서 칸을 찾는 계산은 칸 위치 계산의 역이다`() {
        for (rows in listOf(-4f, -2.5f, -1f, 0f, 0.4f, 1f, 3.2f)) {
            assertEquals(rows, WheelGeometry.rowsAt(WheelGeometry.offsetY(rows, RADIUS), RADIUS), 1e-3f)
        }
        // 휠 밖을 눌러도 옆면의 칸까지만 간다.
        assertEquals(WheelGeometry.rowsAt(RADIUS, RADIUS), WheelGeometry.rowsAt(RADIUS * 3, RADIUS), TOLERANCE)
    }

    private companion object {
        const val RADIUS = 88f
        const val TOLERANCE = 1e-4f
    }
}
