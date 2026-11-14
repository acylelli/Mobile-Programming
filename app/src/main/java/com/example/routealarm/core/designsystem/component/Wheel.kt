package com.example.routealarm.core.designsystem.component

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * 아이폰식 휠 선택기의 공통 부품. 시각 선택기(WheelTimePicker)와 길이 선택기(WheelDurationPicker)가 함께 쓴다.
 *
 * 크기는 아이폰 시계 앱의 알람 편집 화면을 잰 값이다(1pt = 1dp).
 * 휠 반지름 88, 가운데 칸 높이 32(= 반지름 × 한 칸 각도), 알약 모양 선택 띠 높이 34, 글자 24.
 */
private val WheelRadius = 88.dp
private val PickerHeight = 200.dp
private val BandHeight = 34.dp

/** 휠 칸의 글자와, 숫자 옆에 고정해 두는 단위(시간, 분)의 글자 크기 */
internal val WheelTextSize = 24.dp
internal val WheelUnitSize = 17.dp

private val UnitGap = 6.dp

/** 휠들이 차지하는 최대 너비. 그보다 넓은 화면에서는 선택 띠의 양 끝이 휠에 잡히지 않아, 그 자리를 끌면 화면이 스크롤된다. */
private val MaxWheelsWidth = 300.dp

private val FocusRingWidth = 2.dp
private val FocusRingInset = 4.dp

/** 가운데 칸 위아래로 그리는 칸 수. 네 칸 조금 넘어가 원통의 옆면이라 그 너머는 보이지 않는다. */
private const val ROWS_EACH_SIDE = 4

/** 글자 크기 설정이 크면 휠 전체를 함께 키우되, 화면을 넘지 않게 이만큼까지만 키운다. */
private const val MAX_FONT_SCALE = 1.3f

/** 글자 기준선을 칸 가운데에서 글자 크기의 이만큼 아래에 둔다. 숫자 높이(약 0.71em)의 절반이라 숫자가 칸 한가운데에 온다. */
private const val BASELINE_DROP = 0.355f

/** 칸을 넘길 때의 진동 사이 최소 간격. 빠르게 돌 때 진동이 뭉개지지 않게 한다. */
private const val MIN_TICK_INTERVAL_MILLIS = 40L

/** 키보드의 Page Up/Down 으로 한 번에 넘기는 칸 수 */
private const val PAGE_STEP = 5

/**
 * 화면 밀도와 글자 크기 설정을 반영한 휠 크기.
 *
 * @property scale 글자 크기 설정에 따른 확대 비율(1 ~ 1.3)
 * @property radiusPx 원통 반지름
 * @property bandPx 선택 띠 높이
 * @property baselineDropPx 칸 가운데에서 글자 기준선까지의 거리
 */
@Immutable
internal data class WheelMetrics(val scale: Float, val radiusPx: Float, val bandPx: Float, val baselineDropPx: Float) {
    /** 가운데 칸 하나의 높이 */
    val rowPx: Float get() = radiusPx * WheelGeometry.ANGLE_PER_ROW
}

@Composable
internal fun rememberWheelMetrics(): WheelMetrics {
    val density = LocalDensity.current
    return remember(density) {
        val scale = density.fontScale.coerceIn(1f, MAX_FONT_SCALE)
        with(density) {
            WheelMetrics(
                scale = scale,
                radiusPx = (WheelRadius * scale).toPx(),
                bandPx = (BandHeight * scale).toPx(),
                baselineDropPx = (WheelTextSize * scale).toPx() * BASELINE_DROP,
            )
        }
    }
}

/** 휠 글자 스타일. 칸 높이가 정해져 있으므로 글자도 dp 기준으로 잡고 [WheelMetrics.scale] 만큼만 키운다. */
@Composable
internal fun wheelTextStyle(size: Dp, metrics: WheelMetrics, weight: FontWeight = FontWeight.Normal): TextStyle {
    val fontSize = with(LocalDensity.current) { (size * metrics.scale).toSp() }
    return TextStyle(color = RouteAlarmTheme.colors.textPrimary, fontSize = fontSize, fontWeight = weight, fontFeatureSettings = "tnum")
}

/**
 * 휠 묶음의 틀: 가운데 칸 뒤를 가로지르는 알약 모양 선택 띠와, 그 위에 나란히 놓이는 휠들.
 *
 * @param announcement 화면 낭독기가 한 번 읽어 줄 안내(고른 값을 범위 안으로 맞췄을 때 등). 화면에는 보이지 않는다.
 */
@Composable
internal fun WheelPickerFrame(
    metrics: WheelMetrics,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    announcement: String = "",
    wheels: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(PickerHeight * metrics.scale)
            // 안내가 생기면 초점이 다른 곳에 있어도 화면 낭독기가 읽어 준다. 안내가 없을 때는 이름 없는 묶음일 뿐이다.
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (announcement.isNotEmpty()) contentDescription = announcement
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(BandHeight * metrics.scale).background(RouteAlarmTheme.colors.surfaceStrong, CircleShape))
        // 자리 순서(오전/오후 | 시 | 분)와 가운데 선 기준 배치는 글 쓰는 방향과 무관하게 고정한다.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxHeight().widthIn(max = MaxWheelsWidth * metrics.scale).fillMaxWidth().padding(contentPadding),
                content = wheels,
            )
        }
    }
}

/**
 * 휠 하나. 칸 글자를 원통에 감긴 것처럼 그리고, 끌기·튕기기·칸 누르기·키보드 조작을 처리한다.
 * 끌기는 휠이 모두 가져가므로 세로 스크롤 화면 안에 두어도 휠을 돌리는 동안 화면이 따라 움직이지 않는다.
 *
 * @param label 칸 번호에 보여줄 글자
 * @param valueText 화면 낭독기가 읽을 값(예: "오전 3시")
 * @param valueCount 고를 수 있는 값의 수(고리 휠은 한 바퀴의 칸 수)
 * @param description 화면 낭독기가 읽을 휠 이름
 * @param onPick 사용자가 값을 골랐을 때. 손을 떼는 순간 멈출 칸([WheelState.targetIndex])이 정해지므로 휠이 다 멈추기 전에 불린다.
 * @param onRest 사용자가 돌린 휠이 칸에 완전히 멈췄을 때
 * @param labelWidth 글자 칸의 너비. 지정하면 모든 칸이 이 너비의 상자를 함께 써서, 자릿수가 달라도 줄이 맞고 옆 글자가 움직이지 않는다.
 * @param labelTextAlign 글자 칸 안에서의 정렬
 * @param unit 가운데 칸 옆에 고정해서 보여줄 단위(시간, 분)
 */
@Composable
internal fun Wheel(
    state: WheelState,
    label: (Int) -> String,
    valueText: (Int) -> String,
    valueCount: Int,
    description: String,
    style: TextStyle,
    metrics: WheelMetrics,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    onRest: () -> Unit = {},
    contentAlignment: Alignment = Alignment.Center,
    contentPadding: PaddingValues = PaddingValues(),
    labelWidth: Dp = Dp.Unspecified,
    labelTextAlign: TextAlign = TextAlign.Center,
    unit: String? = null,
    unitStyle: TextStyle = style,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val colors = RouteAlarmTheme.colors
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnRest by rememberUpdatedState(onRest)
    val radiusPx = metrics.radiusPx
    val rowHeightPx = metrics.rowPx.roundToInt()
    val outerAlpha = if (colors.isDark) WheelGeometry.OUTER_ALPHA else WheelGeometry.OUTER_ALPHA_ON_LIGHT
    val centered = state.centeredIndex
    // 관성으로 도는 동안에는 지나가는 칸이 아니라 멈출 칸을 값으로 알린다.
    val settled = state.targetIndex
    var focused by remember { mutableStateOf(false) }

    // 손으로 돌리는 동안 칸을 하나 넘길 때마다 톡 하는 진동
    LaunchedEffect(state, view) {
        var lastTick = 0L
        snapshotFlow { state.centeredIndex }.drop(1).collect {
            val now = SystemClock.uptimeMillis()
            if (state.isUserActive && now - lastTick >= MIN_TICK_INTERVAL_MILLIS) {
                lastTick = now
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    // 칸 누르기, 키보드, 화면 낭독기 조작: 고른 칸이 가운데로 오도록 돌린다.
    val select: (Int) -> Unit = { index ->
        scope.launch {
            state.animateTo(index, byUser = true, onTarget = currentOnPick)
            currentOnRest()
        }
    }
    // 지금 값에서 [steps] 칸 옮긴다. 끝이 있는 휠의 끝이라 더 갈 수 없으면 false
    val stepBy: (Int) -> Boolean = { steps ->
        val target = state.clamp(state.targetIndex + steps)
        val moves = target != state.targetIndex
        if (moves) select(target)
        moves
    }
    val nextLabel = stringResource(R.string.cd_wheel_next)
    val previousLabel = stringResource(R.string.cd_wheel_previous)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged { focused = it.isFocused }
            // 키보드: 위아래 화살표로 한 칸, Page Up/Down 으로 여러 칸. 좌우와 Tab 은 옆 휠로 넘어가는 데 쓴다.
            // 끝이 있는 휠의 끝에서 더 갈 수 없는 방향의 키는 삼키지 않아, 초점이 위아래 다른 곳으로 넘어갈 수 있다.
            .onKeyEvent { event ->
                val steps = when (event.key) {
                    Key.DirectionDown -> 1
                    Key.DirectionUp -> -1
                    Key.PageDown -> PAGE_STEP
                    Key.PageUp -> -PAGE_STEP
                    else -> 0
                }
                steps != 0 && event.type == KeyEventType.KeyDown && stepBy(steps)
            }
            .focusable()
            .drawBehind {
                // 키보드 초점이 놓인 휠은 선택 띠의 그 구간에 테두리를 그려 알려준다.
                if (focused) {
                    val stroke = FocusRingWidth.toPx()
                    val inset = FocusRingInset.toPx()
                    val height = metrics.bandPx - stroke
                    drawRoundRect(
                        color = colors.accentText,
                        topLeft = Offset(inset, (size.height - height) / 2f),
                        size = Size(size.width - inset * 2, height),
                        cornerRadius = CornerRadius(height / 2f),
                        style = Stroke(stroke),
                    )
                }
            }
            // 휠 하나를 값 조절 컨트롤 하나로 읽게 한다. 칸 글자들은 접근성 트리에서 빼고 현재 값만 알린다.
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = valueText(settled)
                // 화면 낭독기는 위의 이름과 상태를 읽는다. text 는 UI 테스트 같은 도구가 현재 값을 읽을 수 있게 둔다.
                text = AnnotatedString(valueText(settled))
                val current = WheelRing.slot(settled, valueCount)
                progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..(valueCount - 1).toFloat(), (valueCount - 2).coerceAtLeast(0))
                // 값 조절(위아래 쓸기, 음량 키): 요청한 방향으로 적어도 한 칸은 움직인다.
                setProgress { requested ->
                    stepBy((requested - current).roundToInt().takeIf { it != 0 } ?: sign(requested - current).toInt())
                }
                // 고리 휠은 끝 값에서도 계속 넘길 수 있어야 하므로(59분 다음 0분) 한 칸씩 넘기는 동작을 따로 둔다.
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { stepBy(1) },
                    CustomAccessibilityAction(previousLabel) { stepBy(-1) },
                )
            }
            .pointerInput(state, radiusPx) {
                // 가운데가 아닌 칸을 누르면 그 칸이 가운데로 온다.
                detectTapGestures { offset ->
                    val rows = WheelGeometry.rowsAt(offset.y - size.height / 2f, radiusPx)
                    select(state.clamp((state.position + rows).roundToInt()))
                }
            }
            .draggable(
                state = state.draggableState,
                orientation = Orientation.Vertical,
                // 관성으로 도는 휠은 손을 대는 즉시 잡힌다(누르기로 보지 않는다).
                startDragImmediately = state.isSpinning,
                onDragStarted = { state.onDragStarted() },
                onDragStopped = { velocity ->
                    state.settle(velocity, onTarget = currentOnPick)
                    currentOnRest()
                },
            )
            .clipToBounds()
            .padding(contentPadding),
        contentAlignment = contentAlignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                val rowWidth = if (labelWidth.isSpecified) Modifier.width(labelWidth) else Modifier
                for (index in centered - ROWS_EACH_SIDE..centered + ROWS_EACH_SIDE) {
                    if (!state.contains(index)) continue
                    key(index) {
                        Text(
                            text = label(index),
                            style = style,
                            textAlign = labelTextAlign,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .graphicsLayer {
                                    // 위치는 그리기 단계에서만 읽는다: 돌리는 동안 재구성 없이 레이어만 갱신된다.
                                    val rows = index - state.position
                                    translationY = WheelGeometry.offsetY(rows, radiusPx)
                                    scaleX = WheelGeometry.scaleX(rows)
                                    scaleY = WheelGeometry.scaleY(rows)
                                    alpha = WheelGeometry.alpha(rows, outerAlpha)
                                }
                                .then(rowWidth)
                                .baselineInRow(rowHeightPx, metrics.baselineDropPx),
                        )
                    }
                }
            }
            if (unit != null) {
                Spacer(Modifier.width(UnitGap))
                Text(unit, style = unitStyle, maxLines = 1, softWrap = false, modifier = Modifier.baselineInRow(rowHeightPx, metrics.baselineDropPx))
            }
        }
    }
}

/**
 * 글자를 칸 높이의 상자에 넣고, 기준선이 상자 가운데에서 [dropPx] 아래에 오도록 놓는다.
 * 줄 상자를 기준으로 가운데를 맞추면 한글(대체 글꼴)이 숫자보다 내려앉는다. 기준선을 직접 맞추면 서체가 달라도 숫자와 한글이 한 줄에 선다.
 */
private fun Modifier.baselineInRow(rowPx: Int, dropPx: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val baseline = placeable[FirstBaseline]
    val top = if (baseline == AlignmentLine.Unspecified) (rowPx - placeable.height) / 2 else (rowPx / 2f + dropPx).roundToInt() - baseline
    layout(placeable.width, rowPx) { placeable.place(0, top) }
}

/**
 * 휠을 값에 맞춰 옮기다가 사용자가 그 휠을 잡아 밀려난 경우는 넘어간다(잡은 휠은 놓을 때 스스로 값을 알린다).
 * 이 코루틴 자체가 취소된 경우에는 그대로 취소된다.
 */
internal suspend inline fun unlessPreempted(block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/**
 * 휠 하나의 위치와 움직임.
 *
 * [position] 은 가운데에 놓인 칸 번호를 실수로 나타낸다(3.5 는 3번과 4번 칸의 한가운데).
 * 끌기와 애니메이션이 모두 [draggableState] 를 거치므로, 도는 휠에 손을 대면 애니메이션이 바로 멈춘다.
 *
 * @param rowPx 가운데 칸 하나의 높이(px). 끈 거리를 칸 수로 바꿀 때 쓴다.
 * @param bounds 고를 수 있는 칸 범위. null 이면 끝없이 도는 고리
 */
@Stable
internal class WheelState(
    initialIndex: Int,
    private val rowPx: Float,
    private val bounds: IntRange? = null,
) {
    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set

    /** 가운데에 가장 가까운 칸. 칸을 넘어갈 때만 바뀌므로 이 값을 읽는 쪽은 프레임마다 다시 구성되지 않는다. */
    val centeredIndex: Int by derivedStateOf { clamp(position.roundToInt()) }

    /** 진행 중인 애니메이션이 끝나면 가운데에 올 칸 */
    private var pendingTarget: Int? by mutableStateOf(null)

    /** 지금의 움직임이 끝났을 때 가운데에 올 칸. 멈춰 있거나 끌고 있는 동안에는 [centeredIndex] 와 같다. */
    val targetIndex: Int get() = pendingTarget ?: centeredIndex

    /** 손가락으로 잡고 끄는 중인지. 놓은 뒤의 관성은 포함하지 않는다. */
    var isHeld by mutableStateOf(false)
        private set

    private var runningAnimations by mutableIntStateOf(0)
    private var userDriven = false

    /** 사용자가 돌려서 움직이는 중인지(끌기, 놓은 뒤의 관성, 칸 고르기). 다른 휠이나 바깥 값에 맞춰 옮겨지는 중은 아니다. */
    val isUserActive: Boolean get() = isHeld || (runningAnimations > 0 && userDriven)

    /**
     * 움직임이 중간에 취소되어(따라가던 값이 다시 바뀜 등) 칸과 칸 사이에 걸쳐 멈춰 있는지.
     * 이때는 가운데 칸이 목표와 같아 보여도 제자리가 아니므로, 값에 맞추는 쪽에서 다시 옮겨 줘야 한다.
     */
    val isBetweenRows: Boolean get() = !isHeld && pendingTarget == null && position != centeredIndex.toFloat()

    /** 관성으로 도는 중인지. 이때 손을 대면 칸을 누른 것이 아니라 휠을 잡은 것으로 본다. */
    var isSpinning by mutableStateOf(false)
        private set

    /** 지금의 움직임을 시작한 뒤 [jumpBy] 로 옮겨진 칸 수. 진행 중인 애니메이션이 옮겨진 자리에서 이어지게 한다. */
    private var shift = 0f

    /** 가장 최근에 시작한 움직임의 번호. 밀려난 이전 움직임이 뒤늦게 정리하면서 새 움직임의 상태를 지우지 않게 한다. */
    private var motion = 0

    val draggableState = DraggableState { deltaPx ->
        // 손가락이 아래로 가면 앞 칸이 내려오므로 칸 번호는 줄어든다.
        val delta = -deltaPx / rowPx
        val next = position + delta
        position = if (bounds == null) {
            next
        } else {
            // 끝을 넘겨 끌면 일부만 따라오게 해서 더 갈 곳이 없음을 느끼게 한다.
            val beyondEnd = next < bounds.first || next > bounds.last
            (if (beyondEnd) position + delta * OVERSCROLL_RESISTANCE else next)
                .coerceIn(bounds.first - MAX_OVERSCROLL_ROWS, bounds.last + MAX_OVERSCROLL_ROWS)
        }
    }

    fun contains(index: Int): Boolean = bounds == null || index in bounds

    fun clamp(index: Int): Int = if (bounds == null) index else index.coerceIn(bounds.first, bounds.last)

    fun onDragStarted() {
        motion++
        isHeld = true
        userDriven = true
        pendingTarget = null
        isSpinning = false
    }

    /**
     * 손을 뗐을 때: 멈출 칸을 먼저 정하고, 관성으로 굴러가다 그 칸에 멈춘다.
     * 도중에 다시 잡히거나 다른 움직임에 밀려나면 취소 예외로 끝난다.
     *
     * @param onTarget 멈출 칸이 정해진 직후(굴러가기 전)에 불린다.
     */
    suspend fun settle(velocityPx: Float, onTarget: () -> Unit) {
        isHeld = false
        val velocity = -velocityPx / rowPx
        val plan = WheelMotion.planSettle(position, GlideDecay.calculateTargetValue(0f, velocity), bounds)
        move(plan.target, byUser = true, onTarget) { start ->
            if (plan.glideScale != null) {
                // 자연스럽게 느려지는 곡선은 그대로 쓰고, 멈추는 곳이 칸에 딱 맞도록 이동 거리만 조금 늘이거나 줄인다.
                isSpinning = true
                AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(GlideDecay) {
                    position = start + shift + value * plan.glideScale
                }
                isSpinning = false
            } else {
                val initialVelocity = velocity.coerceIn(-MAX_SNAP_VELOCITY, MAX_SNAP_VELOCITY)
                animate(start, plan.target.toFloat(), initialVelocity, SnapSpring) { value, _ -> position = value + shift }
            }
        }
    }

    /**
     * [index] 칸이 가운데로 오도록 부드럽게 돌린다.
     *
     * @param byUser 사용자가 칸을 골라 생긴 움직임인지. 아니면 다른 휠이나 바깥 값에 맞춰 옮기는 것이다.
     * @param onTarget 목표 칸이 [targetIndex] 에 반영된 직후(돌기 전)에 불린다.
     */
    suspend fun animateTo(index: Int, byUser: Boolean, onTarget: () -> Unit = {}) {
        move(index, byUser, onTarget) { start ->
            animate(start, index.toFloat(), animationSpec = SnapSpring) { value, _ -> position = value + shift }
        }
    }

    /** 애니메이션 없이 [index] 칸으로 옮긴다. */
    suspend fun snapTo(index: Int) {
        move(index, byUser = false, onTarget = {}) {}
    }

    /** 진행 중인 움직임은 그대로 둔 채 위치와 목표만 [rows] 칸 옮긴다. */
    fun jumpBy(rows: Int) {
        shift += rows
        position += rows
        pendingTarget = pendingTarget?.plus(rows)
    }

    /**
     * [target] 칸에서 끝나는 움직임 하나를 실행한다. 진행 중이던 애니메이션은 밀어내고, 손가락으로 잡고 있는 휠은 건드리지 않는다.
     *
     * @param animation 시작 위치(이 움직임을 시작한 뒤의 [jumpBy] 를 뺀 값)를 받아 [position] 을 움직인다.
     */
    private suspend fun move(target: Int, byUser: Boolean, onTarget: () -> Unit, animation: suspend (start: Float) -> Unit) {
        if (isHeld) throw CancellationException("The wheel is being dragged")
        val mine = ++motion
        shift = 0f
        pendingTarget = target
        userDriven = byUser
        isSpinning = false
        runningAnimations++
        try {
            onTarget()
            draggableState.drag(MutatePriority.Default) {
                animation(position - shift)
                position = target + shift
            }
        } finally {
            runningAnimations--
            if (motion == mine) {
                pendingTarget = null
                isSpinning = false
            }
        }
    }

    private companion object {
        const val OVERSCROLL_RESISTANCE = 0.35f
        const val MAX_OVERSCROLL_ROWS = 0.45f

        /** 스냅 스프링에 넘겨줄 처음 속도의 상한(칸/초). 끝이 있는 휠을 세게 튕겨도 멀리 넘어가지 않게 한다. */
        const val MAX_SNAP_VELOCITY = 8f

        /**
         * 관성 감속(칸 단위). 아이폰 스크롤과 같은 감속률(약 0.5초마다 속도가 1/e)로 길게 미끄러진다.
         * 속도가 초당 반 칸 아래로 떨어지면 멈춘 것으로 본다(그때 정확히 목표 칸에 닿도록 거리를 맞춰 둔다).
         */
        val GlideDecay = exponentialDecay<Float>(frictionMultiplier = 0.48f, absVelocityThreshold = 0.5f)
        val SnapSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
}
