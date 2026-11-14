package com.example.routealarm.core.designsystem.component

import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 휠 선택기의 계산 규칙. Compose 의존성이 없어 JVM 단위 테스트로 검증한다.
 *
 * 휠의 위치는 "가운데에 놓인 칸 번호"로 나타낸다. 끝없이 도는 휠(고리)은 칸 번호가 음수이거나
 * 한 바퀴를 넘어도 되고, 실제 값은 [WheelRing.slot] 으로 구한다.
 */

/** 끝없이 도는 휠의 칸 번호 계산 */
internal object WheelRing {
    /** 칸 번호(음수, 여러 바퀴 허용)를 0 until [size] 값으로 바꾼다. */
    fun slot(index: Int, size: Int): Int = Math.floorMod(index, size)

    /** [from] 에서 가장 가까운, 값이 [targetSlot] 인 칸. 정확히 반 바퀴 차이면 값이 커지는 쪽으로 돈다. */
    fun nearestIndex(from: Int, targetSlot: Int, size: Int): Int {
        val forward = Math.floorMod(targetSlot - from, size)
        return if (forward <= size / 2) from + forward else from + forward - size
    }
}

/**
 * 시각(오전/오후 · 시 · 분) 휠.
 *
 * 시 휠은 0~23시를 한 바퀴로 하는 24칸 고리다. 화면에는 12시간제 숫자(12, 1, …, 11)가 두 번 반복되어 보이므로
 * 11 과 12 사이를 지나면 오전/오후가 저절로 바뀌고, 아무리 빨리 돌려도 숫자와 오전/오후가 어긋나지 않는다.
 */
internal object WheelTimeMath {
    const val HOURS_PER_DAY = 24
    const val HOURS_PER_PERIOD = 12
    const val MINUTES_PER_HOUR = 60

    /** 시 휠 칸에 보여줄 12시간제 숫자. 0시와 12시는 12 */
    fun hourLabel(hourIndex: Int): Int = WheelRing.slot(hourIndex, HOURS_PER_PERIOD).let { if (it == 0) HOURS_PER_PERIOD else it }

    fun isPm(hourIndex: Int): Boolean = WheelRing.slot(hourIndex, HOURS_PER_DAY) >= HOURS_PER_PERIOD

    fun minute(minuteIndex: Int): Int = WheelRing.slot(minuteIndex, MINUTES_PER_HOUR)

    fun time(hourIndex: Int, minuteIndex: Int): LocalTime = LocalTime.of(WheelRing.slot(hourIndex, HOURS_PER_DAY), minute(minuteIndex))

    /** 숫자는 그대로 두고 오전/오후만 바꾼 시 휠 칸: 같은 숫자가 보이는 12칸 옆 */
    fun withPeriod(hourIndex: Int, pm: Boolean): Int = when {
        isPm(hourIndex) == pm -> hourIndex
        pm -> hourIndex + HOURS_PER_PERIOD
        else -> hourIndex - HOURS_PER_PERIOD
    }
}

/** 길이(시간 · 분) 휠. 분 휠은 정해진 단위로 도는 고리이고, 시간 휠은 0부터 끝이 있다. */
internal object WheelDurationMath {
    private const val MINUTES_PER_HOUR = 60

    /** 시간 휠의 칸 수: 0시간부터 [maxMinutes] 가 들어가는 시간까지 */
    fun hourCount(maxMinutes: Int): Int = maxMinutes / MINUTES_PER_HOUR + 1

    /** 분 휠 한 바퀴의 칸 수 */
    fun minuteSlots(step: Int): Int = MINUTES_PER_HOUR / step

    fun hourIndex(totalMinutes: Int): Int = totalMinutes / MINUTES_PER_HOUR

    /** [totalMinutes] 의 분 부분이 놓일 칸. 단위에 맞지 않는 값은 아래 칸으로 내린다. */
    fun minuteSlot(totalMinutes: Int, step: Int): Int = totalMinutes % MINUTES_PER_HOUR / step

    /** 분 휠 칸에 보여줄 분 */
    fun minuteLabel(minuteIndex: Int, step: Int): Int = WheelRing.slot(minuteIndex, minuteSlots(step)) * step

    fun totalMinutes(hourIndex: Int, minuteIndex: Int, step: Int): Int = hourIndex * MINUTES_PER_HOUR + minuteLabel(minuteIndex, step)
}

/**
 * 선택기가 스스로 알린 값이 바깥(호이스팅된 상태)을 돌아 다시 들어온 것(메아리)인지, 바깥에서 새로 정한 값인지 가려낸다.
 *
 * 메아리라면 휠은 이미 그 값을 향하고 있으니 그대로 두고, 새 값이라면 돌고 있는 휠도 멈추고 그 값으로 옮겨야 한다.
 * 알린 값은 돌아올 때까지 적어 두고, 들어온 값이 그중에 있으면 메아리로 본다(알린 순서대로 돌아온다고 가정한다).
 */
internal class EchoFilter<T>(initial: T) {
    private var received = initial
    private val awaited = ArrayDeque<T>()

    /** 선택기가 [value] 를 바깥에 알렸다. */
    fun reported(value: T) {
        awaited.addLast(value)
        if (awaited.size > MAX_AWAITED) awaited.removeFirst()
    }

    /** 바깥에서 [value] 가 들어왔다. 선택기가 알린 적 없는 새 값이면 true */
    fun isNew(value: T): Boolean {
        if (value == received) return false
        received = value
        val index = awaited.indexOf(value)
        if (index < 0) {
            awaited.clear()
            return true
        }
        // 이 값과, 그보다 먼저 알렸지만 건너뛰어진 값들은 더 기다리지 않는다.
        repeat(index + 1) { awaited.removeFirst() }
        return false
    }

    private companion object {
        const val MAX_AWAITED = 8
    }
}

/**
 * @property target 멈출 칸
 * @property glideScale 관성 이동 거리에 곱할 비율. null 이면 관성 없이 스프링으로 [target] 에 붙인다.
 */
internal data class SettlePlan(val target: Int, val glideScale: Float?)

/** 손을 뗀 휠의 움직임 */
internal object WheelMotion {
    private const val MIN_GLIDE_ROWS = 1f
    private const val MIN_GLIDE_SCALE = 0.5f
    private const val MAX_GLIDE_SCALE = 1.5f

    /**
     * 손을 뗀 휠이 어디에 어떻게 멈출지 정한다. 멈출 칸을 굴러가기 전에 정해 두므로 휠은 곧장 그 칸으로 미끄러져 들어간다.
     *
     * @param position 지금 가운데에 놓인 칸 번호(실수)
     * @param glideRows 관성만으로 더 굴러갈 칸 수(부호 있음)
     * @param bounds 고를 수 있는 칸 범위. 고리 휠이면 null
     */
    fun planSettle(position: Float, glideRows: Float, bounds: IntRange?): SettlePlan {
        val natural = (position + glideRows).roundToInt()
        val target = if (bounds != null) natural.coerceIn(bounds.first, bounds.last) else natural
        // 관성 이동 거리를 칸에 딱 맞게 조금 늘이거나 줄인다. 너무 짧거나(거의 멈춘 채 놓음) 많이 바꿔야 하면
        // 속도가 튀어 보이므로 그때는 스프링으로 붙인다.
        val scale = if (abs(glideRows) >= MIN_GLIDE_ROWS) (target - position) / glideRows else Float.NaN
        return SettlePlan(target, scale.takeIf { it in MIN_GLIDE_SCALE..MAX_GLIDE_SCALE })
    }
}

/**
 * 원통에 감긴 휠을 정면에서 본 모습. 가운데에서 몇 칸 떨어진 칸이 어디에, 얼마나 작고 흐리게 보이는지 계산한다.
 * 수치는 아이폰 시계 앱의 휠을 잰 값이다: 한 칸 약 20.8도, 반지름 88 에 칸 높이 32, 네 칸 조금 넘어 옆면.
 */
internal object WheelGeometry {
    /** 한 칸이 차지하는 각도(라디안). 반지름에 곱하면 가운데 칸의 높이가 된다. */
    const val ANGLE_PER_ROW = 32f / 88f

    private const val QUARTER_TURN = (Math.PI / 2).toFloat()

    /** 선택 띠 밖의 글자 크기(띠 안 대비). 아이폰은 띠 안을 돋보기처럼 키워 보여준다. */
    private const val OUTER_SCALE = 0.875f

    /** 옆면으로 갈수록 가로로 조금 더 줄어드는 원근(최대 11%) */
    private const val PERSPECTIVE = 0.11f

    /** 선택 띠 밖 글자의 진하기(띠 안 대비). 아이폰 휠을 잰 값 */
    const val OUTER_ALPHA = 0.4f

    /** 밝은 바탕에서는 같은 값이면 옆 칸 숫자가 너무 옅어 읽기 어렵다(대비 3:1 미만). 조금 더 진하게 그린다. */
    const val OUTER_ALPHA_ON_LIGHT = 0.5f

    /** 띠 안팎이 바뀌는 구간(칸). 이보다 가까우면 완전히 띠 안, 멀면 완전히 띠 밖으로 그린다. */
    private const val FOCUS_FULL = 0.27f
    private const val FOCUS_NONE = 0.8f

    /** 띠 밖 글자가 옆면으로 가며 흐려지는 정도: 칸 거리와 그때의 진하기 배율 */
    private val FADE_DISTANCE = floatArrayOf(0f, 1.7f, 2f, 3f, 4f, 4.32f)
    private val FADE_STRENGTH = floatArrayOf(1f, 1f, 0.92f, 0.55f, 0.25f, 0f)

    private fun angle(rows: Float): Float = (rows * ANGLE_PER_ROW).coerceIn(-QUARTER_TURN, QUARTER_TURN)

    /** 가운데로부터의 세로 위치. 옆면으로 갈수록 칸 간격이 좁아진다. */
    fun offsetY(rows: Float, radius: Float): Float = radius * sin(angle(rows))

    /** 가운데에서 세로로 [y] 떨어진 곳에 보이는 칸이 가운데에서 몇 칸 떨어져 있는지(누른 칸 찾기). [offsetY] 의 역 */
    fun rowsAt(y: Float, radius: Float): Float = asin((y / radius).coerceIn(-1f, 1f)) / ANGLE_PER_ROW

    /** 선택 띠 안에 들어온 정도(1 = 한가운데, 0 = 띠 밖) */
    fun focus(rows: Float): Float = ((FOCUS_NONE - abs(rows)) / (FOCUS_NONE - FOCUS_FULL)).coerceIn(0f, 1f)

    fun scaleX(rows: Float): Float = blend(OUTER_SCALE * (1f - PERSPECTIVE * (1f - cos(angle(rows)))), rows)

    /** 원통이 휘면서 세로로는 더 많이 눌려 보인다(옆면에서 0). */
    fun scaleY(rows: Float): Float = blend(OUTER_SCALE * cos(angle(rows)), rows)

    /** @param outerAlpha 선택 띠 밖 글자의 진하기. [OUTER_ALPHA] 또는 [OUTER_ALPHA_ON_LIGHT] */
    fun alpha(rows: Float, outerAlpha: Float = OUTER_ALPHA): Float = blend(outerAlpha * fade(abs(rows)), rows)

    /** 띠 밖 값 [outer] 에서 띠 안 값 1 로, 띠에 들어온 정도만큼 옮긴다. */
    private fun blend(outer: Float, rows: Float): Float = outer + (1f - outer) * focus(rows)

    private fun fade(distance: Float): Float {
        val next = FADE_DISTANCE.indexOfFirst { it > distance }
        if (next < 0) return 0f
        if (next == 0) return FADE_STRENGTH[0]
        val from = FADE_DISTANCE[next - 1]
        val fraction = (distance - from) / (FADE_DISTANCE[next] - from)
        return FADE_STRENGTH[next - 1] + (FADE_STRENGTH[next] - FADE_STRENGTH[next - 1]) * fraction
    }
}
