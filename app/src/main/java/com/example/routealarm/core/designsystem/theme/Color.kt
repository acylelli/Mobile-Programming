package com.example.routealarm.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* 명세의 브랜드 팔레트. Material3 ColorScheme 에 매핑되지 않는 의미색(성공/경고/상태)은 AppColors 로 제공한다. */
val Primary = Color(0xFF4667F2)
val PrimaryDark = Color(0xFF3150CC)
val Accent = Color(0xFF5AC8FA)
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)

val BackgroundLight = Color(0xFFF7F8FC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEEF1F8)
val TextPrimaryLight = Color(0xFF16181D)
val TextSecondaryLight = Color(0xFF747985)
val OutlineLight = Color(0xFFE3E6EF)

val BackgroundDark = Color(0xFF0F1117)
val SurfaceDark = Color(0xFF181B24)
val SurfaceVariantDark = Color(0xFF232733)
val TextPrimaryDark = Color(0xFFF2F3F7)
val TextSecondaryDark = Color(0xFF9AA0AE)
val OutlineDark = Color(0xFF2E3340)

/**
 * Material3 가 제공하지 않는 의미 색상. 교통 상태 Chip 등에서 사용한다.
 * 색만으로 상태를 전달하지 않도록 UI 는 항상 텍스트를 함께 표시한다.
 */
@Immutable
data class AppColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val accent: Color,
    val textSecondary: Color,
    val cardShadow: Color,
    val walkSegment: Color,
    val busSegment: Color,
    val subwaySegment: Color,
    val trainSegment: Color,
)

val LightAppColors = AppColors(
    success = Success,
    warning = Warning,
    danger = Error,
    accent = Accent,
    textSecondary = TextSecondaryLight,
    cardShadow = Color(0x1416181D),
    walkSegment = TextSecondaryLight,
    busSegment = Color(0xFF3D8F3D),
    subwaySegment = Primary,
    trainSegment = Color(0xFF8B5CF6),
)

val DarkAppColors = AppColors(
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    accent = Accent,
    textSecondary = TextSecondaryDark,
    cardShadow = Color(0x00000000),
    walkSegment = TextSecondaryDark,
    busSegment = Color(0xFF6CC56C),
    subwaySegment = Color(0xFF7D95FF),
    trainSegment = Color(0xFFA78BFA),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
