package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing

/** 앱 공통 바텀시트: 흰 바탕, 28dp 라운드, 짧은 회색 손잡이(PRO 페이월, 설정 값 선택) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = RouteAlarmTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 10.dp)
                    .size(width = 46.dp, height = 4.dp)
                    .background(colors.outline, CircleShape),
            )
        },
        content = content,
    )
}

/** 분 단위 값을 고르는 시트(기본 준비 시간, 기본 여유 시간, 가상 지연) */
@Composable
fun MinutePickerSheet(
    title: String,
    initial: Int,
    range: IntRange,
    presets: List<Int>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
) {
    val colors = RouteAlarmTheme.colors
    var value by remember { mutableIntStateOf(initial.coerceIn(range.first, range.last)) }
    AppBottomSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            if (body != null) {
                Spacer(Modifier.height(6.dp))
                Paragraph(body)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.common_minutes_format, value),
                style = MaterialTheme.typography.displayMedium,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            MinuteSlider(value, range, { value = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                presets.forEach { preset ->
                    ChoicePill(stringResource(R.string.common_minutes_format, preset), selected = value == preset, onClick = { value = preset })
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(stringResource(R.string.common_confirm), onClick = { onConfirm(value) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 여러 값 중 하나를 고르는 시트(알람음, 다시 울림 간격). 고르면 바로 적용하고 닫는다. */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    AppBottomSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = 16.dp).selectableGroup()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.tile)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(horizontal = 4.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioDot(isSelected)
                    Spacer(Modifier.width(14.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                }
            }
        }
    }
}
