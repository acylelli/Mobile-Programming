package com.example.routealarm.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.policy.AlarmStats
import com.example.routealarm.domain.policy.AlarmStatsPolicy
import com.example.routealarm.domain.policy.DayWake
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsRoute(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(state = state, onBack = onBack)
}

/** 기록·통계: 최근 30일 제시간 기상률, 늦게 끈 시간, 다시 울림, 교통 변화로 조정한 횟수, 최근 14일 막대 그래프 */
@Composable
fun StatsScreen(state: StatsUiState, onBack: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        BackHeader(onBack = onBack, title = stringResource(R.string.stats_title))
        val stats = state.stats
        when {
            stats == null -> LoadingState(Modifier.padding(top = Spacing.xxl))
            stats.mornings == 0 && stats.adjustments == 0 && stats.snoozes == 0 -> EmptyState(
                title = stringResource(R.string.stats_empty_title),
                body = stringResource(R.string.stats_empty_body),
                icon = Icons.Outlined.BarChart,
                modifier = Modifier.padding(top = Spacing.xl),
            )
            else -> StatsContent(stats)
        }
    }
}

@Composable
private fun StatsContent(stats: AlarmStats) {
    val colors = RouteAlarmTheme.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.stats_period, stats.mornings), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stats.onTimeRate?.let { stringResource(R.string.stats_rate_value, it) } ?: "—",
                style = MaterialTheme.typography.displayLarge,
                color = colors.textPrimary,
                modifier = Modifier.testTag("stats_on_time_rate"),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.stats_on_time_label, AlarmStatsPolicy.ON_TIME_MINUTES),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.stats_average_late), stringResource(R.string.common_minutes_format, stats.averageLateMinutes), Modifier.weight(1f))
            StatTile(stringResource(R.string.stats_snoozes), stringResource(R.string.stats_count_format, stats.snoozes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.stats_adjustments), stringResource(R.string.stats_count_format, stats.adjustments), Modifier.weight(1f))
            StatTile(stringResource(R.string.stats_adjusted_earlier), stringResource(R.string.common_minutes_format, stats.adjustedEarlierMinutes), Modifier.weight(1f))
        }

        Spacer(Modifier.height(Spacing.lg))
        SectionLabel(stringResource(R.string.stats_chart_title, AlarmStatsPolicy.CHART_DAYS))
        Spacer(Modifier.height(10.dp))
        SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
            WakeChart(stats.days, Modifier.padding(horizontal = 14.dp, vertical = 16.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.stats_chart_note), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    SurfaceCard(modifier, shape = Radius.group) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        }
    }
}

/**
 * 하루에 막대 하나. 높이는 늦게 끈 시간(30분에서 자름), 제시간(5분 이내)이면 회색, 늦으면 강조색.
 * 알람이 없던 날은 바닥에 작은 점만 찍는다.
 */
@Composable
private fun WakeChart(days: List<DayWake>, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val late = day.lateMinutes
            val description = if (late == null) {
                stringResource(R.string.stats_day_none, day.date.monthValue, day.date.dayOfMonth)
            } else {
                stringResource(R.string.stats_day_late, day.date.monthValue, day.date.dayOfMonth, late)
            }
            Column(
                Modifier.weight(1f).semantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(CHART_HEIGHT), contentAlignment = Alignment.BottomCenter) {
                    if (late == null) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(colors.dotted))
                    } else {
                        val fraction = (late.coerceIn(1, CHART_CAP_MINUTES).toFloat() / CHART_CAP_MINUTES).coerceAtLeast(MIN_BAR_FRACTION)
                        Box(
                            Modifier
                                .width(12.dp)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (late <= AlarmStatsPolicy.ON_TIME_MINUTES) colors.textTertiary else colors.accent),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val CHART_HEIGHT = 96.dp
private const val CHART_CAP_MINUTES = 30
private const val MIN_BAR_FRACTION = 0.08f
