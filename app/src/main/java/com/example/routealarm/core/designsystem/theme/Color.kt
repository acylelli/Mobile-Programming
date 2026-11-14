package com.example.routealarm.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.routealarm.domain.model.ColorTheme

/**
 * UI 설계서 시안 C(Route Line)의 역할별 색 토큰.
 *
 * 화면은 "주황" 같은 구체적인 색이 아니라 [accent], [button] 같은 역할만 참조한다.
 * 덕분에 테마 선택(UI-F-002)에서 고른 컬러 테마를 이 토큰만 바꿔 앱 전체에 반영할 수 있다.
 * 지하철 노선색처럼 정보를 전달하는 색은 토큰이 아니라 고정값이다([transitLineColor]).
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    /** 카드·입력창·설정 묶음의 옅은 면 */
    val surface: Color,
    /** 칩·세그먼트 컨트롤처럼 한 단계 더 진한 면 */
    val surfaceStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val outline: Color,
    val divider: Color,
    /** 준비 구간 점선, 점선 카드 테두리 */
    val dotted: Color,
    /** 브랜드 색: 기상·도착 지점, 켜진 스위치, 선택 테두리, 알람 화면 배경 */
    val accent: Color,
    val onAccent: Color,
    /** 작은 글자에 쓰는 진한 강조색. 배경과의 대비를 확보한다(INR-002). */
    val accentText: Color,
    val accentContainer: Color,
    /** 화면의 주 행동 버튼 */
    val button: Color,
    val onButton: Color,
    /** 지연·오류 상태색. 테마와 관계없이 빨강 계열로 고정 */
    val danger: Color,
    val dangerContainer: Color,
    val walkSegment: Color,
    val switchOff: Color,
)

private val LightNeutral = AppColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F4F1),
    surfaceStrong = Color(0xFFF0EFEC),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF6B6B6B),
    textTertiary = Color(0xFF9A9A9A),
    outline = Color(0xFFE7E5E0),
    divider = Color(0xFFECEAE5),
    dotted = Color(0xFFCFCBC3),
    accent = Color(0xFFFF5A1F),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFFC2410C),
    accentContainer = Color(0xFFFFF4EF),
    button = Color(0xFF111111),
    onButton = Color(0xFFFFFFFF),
    danger = Color(0xFFDC2626),
    dangerContainer = Color(0xFFFDECEC),
    walkSegment = Color(0xFFA3A3A3),
    switchOff = Color(0xFFD6D3CD),
)

private val DarkNeutral = AppColors(
    isDark = true,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1C),
    surfaceStrong = Color(0xFF2A2A27),
    textPrimary = Color(0xFFF5F4F1),
    textSecondary = Color(0xFFA8A6A0),
    textTertiary = Color(0xFF7C7A75),
    outline = Color(0xFF34332F),
    divider = Color(0xFF2A2A27),
    dotted = Color(0xFF4A4843),
    accent = Color(0xFFFF5A1F),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFFFF8A5B),
    accentContainer = Color(0xFF3A1C10),
    button = Color(0xFFF5F4F1),
    onButton = Color(0xFF111111),
    danger = Color(0xFFF87171),
    dangerContainer = Color(0xFF3B1414),
    walkSegment = Color(0xFF6E6C67),
    switchOff = Color(0xFF45433F),
)

/** 컬러 테마와 화면 밝기(기기 다크 모드)에 맞는 토큰 묶음. 나이트 테마는 기기 설정과 관계없이 항상 어둡다. */
fun appColorsFor(theme: ColorTheme, dark: Boolean): AppColors = when (theme) {
    ColorTheme.DEFAULT -> if (dark) DarkNeutral else LightNeutral

    ColorTheme.DAWN -> if (dark) {
        DarkNeutral.copy(
            background = Color(0xFF14172B),
            surface = Color(0xFF1E2240),
            surfaceStrong = Color(0xFF272C50),
            textPrimary = Color(0xFFF4EFE8),
            textSecondary = Color(0xFFA9A7B8),
            textTertiary = Color(0xFF7A7890),
            outline = Color(0xFF30355C),
            divider = Color(0xFF272C50),
            dotted = Color(0xFF454A72),
            accent = Color(0xFFF2B98B),
            onAccent = Color(0xFF1B2140),
            accentText = Color(0xFFF2B98B),
            accentContainer = Color(0xFF3A2F3A),
            button = Color(0xFFF4EFE8),
            onButton = Color(0xFF1B2140),
            walkSegment = Color(0xFF6C6F8E),
            switchOff = Color(0xFF3A3F66),
        )
    } else {
        LightNeutral.copy(
            background = Color(0xFFFBF7F2),
            surface = Color(0xFFF3EDE4),
            surfaceStrong = Color(0xFFECE5DA),
            textPrimary = Color(0xFF1B2140),
            textSecondary = Color(0xFF6A6676),
            textTertiary = Color(0xFF9A96A3),
            outline = Color(0xFFE6DFD3),
            divider = Color(0xFFEDE7DC),
            dotted = Color(0xFFCFC8BA),
            accent = Color(0xFF1B2140),
            onAccent = Color(0xFFFBF7F2),
            accentText = Color(0xFF1B2140),
            accentContainer = Color(0xFFE9E6EE),
            button = Color(0xFF1B2140),
            onButton = Color(0xFFFBF7F2),
            switchOff = Color(0xFFD9D2C6),
        )
    }

    ColorTheme.NIGHT -> DarkNeutral.copy(
        background = Color(0xFF0E1220),
        surface = Color(0xFF181D30),
        surfaceStrong = Color(0xFF222840),
        textPrimary = Color(0xFFF2F3F7),
        textSecondary = Color(0xFFA0A6BA),
        textTertiary = Color(0xFF6F7590),
        outline = Color(0xFF2A3048),
        divider = Color(0xFF222840),
        dotted = Color(0xFF474A56),
        accent = Color(0xFFC8F560),
        onAccent = Color(0xFF0E1220),
        accentText = Color(0xFFC8F560),
        accentContainer = Color(0xFF26331A),
        button = Color(0xFFC8F560),
        onButton = Color(0xFF0E1220),
        walkSegment = Color(0xFF6F7590),
        switchOff = Color(0xFF353B57),
    )
}

/**
 * 역할 토큰 → Material3 ColorScheme. 다이얼로그·날짜 선택기 같은 기본 M3 컴포넌트도 같은 색을 쓰게 한다.
 * Dynamic color 는 쓰지 않는다: 사용자가 고른 컬러 테마가 기기 배경화면에 따라 바뀌면 안 되기 때문이다.
 */
fun AppColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = accentText,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = surfaceStrong,
        onSecondaryContainer = textPrimary,
        tertiary = accentText,
        onTertiary = background,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = accentText,
        background = background,
        onBackground = textPrimary,
        surface = background,
        onSurface = textPrimary,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        surfaceDim = surfaceStrong,
        surfaceBright = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = if (isDark) surface else background,
        surfaceContainer = if (isDark) surface else background,
        surfaceContainerHigh = if (isDark) surface else background,
        surfaceContainerHighest = if (isDark) surfaceStrong else surface,
        outline = dotted,
        outlineVariant = outline,
        error = danger,
        onError = Color.White,
        errorContainer = dangerContainer,
        onErrorContainer = danger,
        scrim = Color.Black,
    )
}

val LocalAppColors = staticCompositionLocalOf { LightNeutral }
