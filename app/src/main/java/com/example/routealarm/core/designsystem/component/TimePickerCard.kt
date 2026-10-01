package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.routealarm.core.designsystem.theme.Spacing
import java.time.LocalTime

/** Material3 TimePicker 를 카드로 감싼 공통 컴포넌트. 값은 LocalTime 으로 호이스팅된다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerCard(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    LaunchedEffect(state) {
        snapshotFlow { LocalTime.of(state.hour, state.minute) }.collect(onTimeChange)
    }
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.sm))
            }
            TimePicker(state = state)
        }
    }
}
