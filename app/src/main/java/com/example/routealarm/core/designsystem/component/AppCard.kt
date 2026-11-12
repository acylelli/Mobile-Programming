package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme

/**
 * 흰 바탕에 옅은 테두리만 있는 카드(알람 카드, 요금제, 권한 안내). 그림자는 쓰지 않는다.
 * [selected] 면 강조색 테두리와 옅은 강조 배경으로 바뀐다. 선택 여부는 테두리 굵기로도 구분된다.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    shape: Shape = Radius.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    val container = if (selected) colors.accentContainer else colors.background
    val border = if (selected) BorderStroke(1.5.dp, colors.accent) else BorderStroke(1.dp, colors.outline)
    CardSurface(modifier, onClick, shape, container, colors.textPrimary, border, content)
}

/** 따뜻한 회색 면의 카드(시간 내역, 설정 묶음, 확인된 이용권) */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = Radius.card,
    containerColor: Color = RouteAlarmTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    CardSurface(modifier, onClick, shape, containerColor, RouteAlarmTheme.colors.textPrimary, null, content)
}

@Composable
private fun CardSurface(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    shape: Shape,
    container: Color,
    content: Color,
    border: BorderStroke?,
    body: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, contentColor = content, border = border) {
            Column(content = body)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = container, contentColor = content, border = border) {
            Column(content = body)
        }
    }
}

/** 점선 테두리(플랜 한도 카드). Compose 의 border 는 점선을 지원하지 않아 직접 그린다. */
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.5.dp, dash: Dp = 6.dp, gap: Dp = 5.dp): Modifier =
    drawBehind {
        val stroke = strokeWidth.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
        )
    }

/** 카드 안의 점선 구분선 */
fun Modifier.dashedLine(color: Color, strokeWidth: Dp = 1.5.dp, dash: Dp = 5.dp, gap: Dp = 4.dp): Modifier =
    drawBehind {
        val y = size.height / 2
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        )
    }
