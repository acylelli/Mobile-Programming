package com.example.routealarm.presentation.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.core.designsystem.theme.appColorsFor
import com.example.routealarm.core.designsystem.theme.isAppInDarkTheme
import com.example.routealarm.domain.model.ColorTheme
import com.example.routealarm.presentation.common.SampleLineColors
import com.example.routealarm.presentation.settings.themeName

private const val GRID_COLUMNS = 3

@Composable
fun ThemeRoute(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ThemeScreen(
        state = state,
        onBack = onBack,
        onApply = { theme ->
            viewModel.apply(theme)
            onBack()
        },
    )
}

/**
 * 테마 선택 — UI-F-002.
 * 컬러 테마 5종 가운데 하나를 고른다(모든 플랜에서 무료). 고른 값은 [적용하기]를 눌러야 앱 전체에 반영된다.
 * 화면 모드는 따로 고르지 않고 기기 설정을 따른다.
 */
@Composable
fun ThemeScreen(
    state: ThemeUiState,
    onBack: () -> Unit,
    onApply: (ColorTheme) -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    // 저장된 값이 로드되면 그 값에서 시작하고, 이후에는 사용자가 고른 값을 유지한다.
    var theme by rememberSaveable(state.savedTheme) { mutableStateOf(state.savedTheme) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        BackHeader(onBack = onBack, title = stringResource(R.string.settings_theme))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            // 컬러 테마: 미리보기 카드
            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.theme_color_section))
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorTheme.entries.chunked(GRID_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            ThemeCard(
                                theme = item,
                                selected = item == theme,
                                onClick = { theme = item },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // 노선색 고정 안내와 어두운 화면 안내
            Spacer(Modifier.height(18.dp))
            ThemeNotice(Icons.Outlined.Place, stringResource(R.string.theme_line_color_notice))
            Spacer(Modifier.height(10.dp))
            ThemeNotice(Icons.Outlined.DarkMode, stringResource(R.string.theme_dark_notice))
            Spacer(Modifier.height(Spacing.lg))
        }
        // 적용
        PrimaryButton(
            text = stringResource(R.string.theme_apply),
            onClick = { onApply(theme) },
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun ThemeNotice(icon: ImageVector, text: String) {
    Row {
        Icon(icon, contentDescription = null, tint = RouteAlarmTheme.colors.textSecondary, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Paragraph(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ThemeCard(theme: ColorTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.outline, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(8.dp),
        ) {
            ThemePreview(theme)
            Text(
                themeName(theme),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                color = colors.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 4.dp),
            )
        }
        // 선택한 테마는 오른쪽 위에 체크로 표시한다.
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(24.dp)
                    .background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * 테마의 바탕·강조색으로 그린 작은 화면 미리보기. 적용했을 때와 같은 밝기(기기 다크 모드 반영)로 그린다.
 * 가운데 노선은 어느 테마에서나 같은 노선색이다.
 */
@Composable
private fun ThemePreview(theme: ColorTheme) {
    val dark = isAppInDarkTheme(theme)
    val preview = remember(theme, dark) { appColorsFor(theme, dark) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.12f)
            .background(preview.background, shape)
            .border(1.dp, if (preview.isDark) preview.background else RouteAlarmTheme.colors.outline, shape)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 40.dp, height = 10.dp).background(preview.accent, CircleShape))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(preview.accent, CircleShape))
            Box(Modifier.weight(1f).height(3.dp).background(SampleLineColors.Line8))
            Box(Modifier.size(8.dp).background(preview.textPrimary, CircleShape))
            Box(Modifier.weight(1f).height(3.dp).background(SampleLineColors.Line2))
            Box(Modifier.size(8.dp).background(preview.accent, CircleShape))
        }
        Box(Modifier.fillMaxWidth(0.8f).height(4.dp).background(preview.dotted, CircleShape))
        Box(Modifier.fillMaxWidth(0.5f).height(4.dp).background(preview.dotted, CircleShape))
    }
}
