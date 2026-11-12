package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import java.time.LocalTime

/** Material3 TimePicker 를 카드로 감싼 공통 컴포넌트. 값은 LocalTime 으로 호이스팅된다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerCard(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RouteAlarmTheme.colors
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    LaunchedEffect(state) {
        snapshotFlow { LocalTime.of(state.hour, state.minute) }.collect(onTimeChange)
    }
    AppCard(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.surface,
                    selectorColor = colors.accent,
                    clockDialSelectedContentColor = colors.onAccent,
                    clockDialUnselectedContentColor = colors.textPrimary,
                    timeSelectorSelectedContainerColor = colors.accentContainer,
                    timeSelectorSelectedContentColor = colors.accentText,
                    timeSelectorUnselectedContainerColor = colors.surface,
                    timeSelectorUnselectedContentColor = colors.textPrimary,
                ),
            )
        }
    }
}
