package com.example.routealarm.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.example.routealarm.domain.model.ColorTheme

/**
 * 실제로 어두운 화면인지 결정한다. 나이트 테마는 항상 어둡고, 나머지 테마는 기기의 다크 모드 설정을 따른다.
 * Activity 의 시스템 바 스타일과 테마 미리보기도 이 값을 쓴다.
 */
@Composable
fun isAppInDarkTheme(colorTheme: ColorTheme): Boolean =
    colorTheme == ColorTheme.NIGHT || isSystemInDarkTheme()

@Composable
fun RouteAlarmTheme(
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(colorTheme)
    val colors = remember(colorTheme, dark) { appColorsFor(colorTheme, dark) }
    val colorScheme = remember(colors) { colors.toColorScheme() }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object RouteAlarmTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}
