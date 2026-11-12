package com.example.routealarm.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 카드·버튼은 20dp 안팎의 큰 라운드, 바텀시트는 28dp */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** 화면 좌우 여백 */
    val screen = 24.dp
}

object Radius {
    val card = RoundedCornerShape(22.dp)
    val group = RoundedCornerShape(20.dp)
    val button = RoundedCornerShape(20.dp)
    val field = RoundedCornerShape(18.dp)
    val tile = RoundedCornerShape(12.dp)
}
