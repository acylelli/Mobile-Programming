package com.example.routealarm.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 하단 탭과 노선도 지점에 쓰는 선 아이콘. 설계서 목업과 같은 2dp 둥근 선으로 직접 그린다.
 * (Material 기본 아이콘은 면이 채워진 형태라 목업의 가벼운 선 느낌과 달라진다.)
 */
object AppIcons {

    val Home: ImageVector by lazy {
        strokeIcon("Home") {
            moveTo(3.5f, 11f)
            lineTo(12f, 3.8f)
            lineTo(20.5f, 11f)
            moveTo(5.8f, 9.4f)
            verticalLineTo(20f)
            horizontalLineTo(18.2f)
            verticalLineTo(9.4f)
        }
    }

    val Alarm: ImageVector by lazy {
        strokeIcon("Alarm") {
            circle(12f, 13f, 7.5f)
            // 시침·분침
            moveTo(12f, 9f)
            verticalLineTo(13f)
            lineTo(14.6f, 14.8f)
            // 양쪽 종
            moveTo(5.2f, 3.4f)
            lineTo(2.6f, 5.8f)
            moveTo(18.8f, 3.4f)
            lineTo(21.4f, 5.8f)
        }
    }

    /** 설정 탭: 가운데 원과 여덟 방향 선 */
    val Settings: ImageVector by lazy {
        strokeIcon("Settings") {
            circle(12f, 12f, 3.2f)
            moveTo(12f, 3.2f); verticalLineTo(5.6f)
            moveTo(12f, 18.4f); verticalLineTo(20.8f)
            moveTo(3.2f, 12f); horizontalLineTo(5.6f)
            moveTo(18.4f, 12f); horizontalLineTo(20.8f)
            moveTo(6.1f, 6.1f); lineTo(7.7f, 7.7f)
            moveTo(16.3f, 16.3f); lineTo(17.9f, 17.9f)
            moveTo(17.9f, 6.1f); lineTo(16.3f, 7.7f)
            moveTo(7.7f, 16.3f); lineTo(6.1f, 17.9f)
        }
    }

    private fun strokeIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
            pathBuilder = block,
        ).build()

    /** 중심 (cx, cy), 반지름 r 인 원을 두 개의 호로 그린다. */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
    }
}
