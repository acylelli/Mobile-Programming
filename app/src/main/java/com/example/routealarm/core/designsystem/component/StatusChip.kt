package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.domain.model.TrafficStatus

/** 둥근 알약 모양 칩. 구간 시간, 교통 상태, 배지에 공통으로 쓴다. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = RouteAlarmTheme.colors.surfaceStrong,
    contentColor: Color = RouteAlarmTheme.colors.textSecondary,
    icon: ImageVector? = null,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Row(
        modifier = modifier
            .background(containerColor, CircleShape)
            .padding(horizontal = if (icon != null) 10.dp else 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = style, color = contentColor, maxLines = 1)
    }
}

/**
 * 교통 상태 칩. 색만으로 판단하지 않도록 아이콘과 문구를 항상 함께 보여준다.
 * 정상은 무채색 체크, 지연은 빨강 경고 아이콘이다(UI-B-001 ②).
 */
@Composable
fun TrafficStatusChip(status: TrafficStatus, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    val description = stringResource(R.string.cd_traffic_status)
    when (status) {
        TrafficStatus.Normal -> {
            val text = stringResource(R.string.traffic_normal)
            Pill(
                text = text,
                icon = Icons.Rounded.Check,
                containerColor = colors.surfaceStrong,
                contentColor = colors.textPrimary,
                modifier = modifier.clearAndSetSemantics { contentDescription = "$description: $text" },
            )
        }
        is TrafficStatus.MinorDelay, is TrafficStatus.SevereDelay -> {
            val text = if (status is TrafficStatus.SevereDelay) {
                stringResource(R.string.traffic_severe_delay, status.delayMinutes)
            } else {
                stringResource(R.string.traffic_minor_delay, (status as TrafficStatus.MinorDelay).delayMinutes)
            }
            Pill(
                text = text,
                icon = Icons.Rounded.WarningAmber,
                containerColor = colors.dangerContainer,
                contentColor = colors.danger,
                modifier = modifier.clearAndSetSemantics { contentDescription = "$description: $text" },
            )
        }
    }
}

/** PRO 전용 기능 표시. [dim] 은 점선 한도 카드처럼 덜 강조해야 할 때 쓴다. */
@Composable
fun ProBadge(modifier: Modifier = Modifier, dim: Boolean = false) {
    val colors = RouteAlarmTheme.colors
    Pill(
        text = stringResource(R.string.badge_pro),
        containerColor = colors.textPrimary,
        contentColor = if (dim) colors.textTertiary else colors.background,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}

/** 강조색 배경의 PRO 표시(검정 배너 위) */
@Composable
fun ProAccentBadge(modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    Pill(
        text = stringResource(R.string.badge_pro),
        containerColor = colors.accent,
        contentColor = colors.onAccent,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
    )
}

/** 필수·선택·추천·무료·예시 같은 작은 꼬리표 */
@Composable
fun TagBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = RouteAlarmTheme.colors.surfaceStrong,
    contentColor: Color = RouteAlarmTheme.colors.textSecondary,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        maxLines = 1,
        modifier = modifier
            .background(containerColor, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
    TagBadge(stringResource(R.string.demo_badge), modifier)
}

@Preview(showBackground = true)
@Composable
private fun StatusChipPreview() {
    RouteAlarmTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TrafficStatusChip(TrafficStatus.Normal)
            TrafficStatusChip(TrafficStatus.MinorDelay(8))
            TrafficStatusChip(TrafficStatus.SevereDelay(18))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProBadge()
                ProBadge(dim = true)
                ProAccentBadge()
                DemoBadge()
            }
        }
    }
}
