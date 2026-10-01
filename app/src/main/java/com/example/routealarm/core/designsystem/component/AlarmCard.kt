package com.example.routealarm.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.repeatText

/**
 * 알람 목록의 한 줄 카드: 반복 요일 / 기상 시각 / 도착지 / 목표 도착 / ON-OFF 스위치
 */
@Composable
fun AlarmCard(
    schedule: Schedule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeColor by animateColorAsState(
        if (schedule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "alarmTimeColor",
    )
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repeatText(schedule.repeatDays, schedule.oneTimeDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (schedule.isDemo) {
                        Spacer(Modifier.width(Spacing.sm))
                        DemoBadge()
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    schedule.plan?.wakeUp?.let(TimeFormat::time) ?: "--:--",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = timeColor,
                )
                Text(
                    schedule.destination.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.alarms_target_format, TimeFormat.time(schedule.targetArrivalTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val toggleDescription = stringResource(R.string.alarms_toggle_description)
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            )
        }
    }
}

@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.demo_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** 작은 라벨-값 쌍 (준비 시간 / 30분) */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, horizontalAlignment: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = horizontalAlignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
