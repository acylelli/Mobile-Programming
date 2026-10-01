package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.domain.model.TrafficStatus

/**
 * 색 점 + 텍스트로 상태를 표시한다. 색만으로 의미를 전달하지 않기 위해 텍스트는 항상 함께 나온다.
 */
@Composable
fun StatusChip(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .background(containerColor, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

@Composable
fun TrafficStatusChip(status: TrafficStatus, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.appColors
    val (text, color) = when (status) {
        TrafficStatus.Normal -> stringResource(R.string.traffic_normal) to colors.success
        is TrafficStatus.MinorDelay -> stringResource(R.string.traffic_minor_delay, status.delayMinutes) to colors.warning
        is TrafficStatus.SevereDelay -> stringResource(R.string.traffic_severe_delay, status.delayMinutes) to colors.danger
    }
    StatusChip(text = text, dotColor = color, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun StatusChipPreview() {
    RouteAlarmTheme {
        androidx.compose.foundation.layout.Column {
            TrafficStatusChip(TrafficStatus.Normal)
            TrafficStatusChip(TrafficStatus.MinorDelay(7))
            TrafficStatusChip(TrafficStatus.SevereDelay(18))
        }
    }
}
