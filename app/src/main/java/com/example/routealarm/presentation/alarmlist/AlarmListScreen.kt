package com.example.routealarm.presentation.alarmlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.AppSwitch
import com.example.routealarm.core.designsystem.component.DemoBadge
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.Pill
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.ProBadge
import com.example.routealarm.core.designsystem.component.ScreenHeader
import com.example.routealarm.core.designsystem.component.TrafficStatusChip
import com.example.routealarm.core.designsystem.component.dashedBorder
import com.example.routealarm.core.designsystem.component.dashedLine
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.ScheduleQuota
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.relativeUpdateText
import com.example.routealarm.presentation.common.rememberNow
import com.example.routealarm.presentation.common.repeatLabel
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import java.time.Instant

@Composable
fun AlarmListRoute(
    onCreateSchedule: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onShowPaywall: () -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AlarmListEvent.OpenCreate -> onCreateSchedule()
                AlarmListEvent.ShowPaywall -> onShowPaywall()
                is AlarmListEvent.ShowLimitMessage -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.alarms_limit_message, event.limit),
                        actionLabel = context.getString(R.string.alarms_limit_action),
                    )
                    if (result == SnackbarResult.ActionPerformed) onShowPaywall()
                }
                is AlarmListEvent.ShowError -> snackbar.showSnackbar(context.getString(event.error.messageRes()))
            }
        }
    }

    AlarmListScreen(
        state = state,
        onAdd = viewModel::onAddClicked,
        onOpenSchedule = onOpenSchedule,
        onToggle = viewModel::toggle,
        onShowPaywall = onShowPaywall,
    )
}

/** 알람 목록 — UI-D-001 */
@Composable
fun AlarmListScreen(
    state: AlarmListUiState,
    onAdd: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onShowPaywall: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    val now by rememberNow()
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = stringResource(R.string.alarms_title),
            trailing = { AddButton(onClick = onAdd) },
        )
        when (state) {
            AlarmListUiState.Loading -> LoadingState(Modifier.padding(top = Spacing.xxl))

            is AlarmListUiState.Content -> if (state.items.isEmpty()) {
                Spacer(Modifier.height(Spacing.xl))
                EmptyState(
                    title = stringResource(R.string.alarms_empty_title),
                    body = stringResource(R.string.alarms_empty_body),
                    action = { PrimaryButton(text = stringResource(R.string.home_new_schedule), onClick = onAdd) },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 20.dp, bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(state.items, key = { it.schedule.id }) { item ->
                        AlarmCard(
                            item = item,
                            now = now,
                            onClick = { onOpenSchedule(item.schedule.id) },
                            onToggle = { onToggle(item.schedule.id, it) },
                        )
                    }
                    (state.quota as? ScheduleQuota.Limited)?.let { quota ->
                        item(key = "plan-limit") { PlanLimitCard(quota, onClick = onShowPaywall) }
                    }
                }
            }
        }
    }
}

/** 새 일정(①): 5단계 일정 만들기를 시작하는 검정 원형 버튼 */
@Composable
private fun AddButton(onClick: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    val description = stringResource(R.string.cd_add_alarm)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.button)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onButton, modifier = Modifier.size(26.dp))
    }
}

/**
 * 알람 카드(②): 반복 요일, 기상 시각, 목적지, 목표 도착 시각, 출발 시각. 누르면 일정 상세로 간다.
 * 교통 변화로 조정된 일정은 빨강 경고 칩과 "변경 전 → 변경 후" 시각으로 표시한다(④).
 */
@Composable
private fun AlarmCard(item: AlarmListItem, now: Instant, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = RouteAlarmTheme.colors
    val schedule = item.schedule
    val plan = schedule.plan
    val timeColor by animateColorAsState(if (schedule.enabled) colors.textPrimary else colors.textTertiary, label = "alarmTime")

    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    repeatLabel(schedule.repeatDays, schedule.oneTimeDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                if (schedule.isDemo) {
                    Spacer(Modifier.width(8.dp))
                    DemoBadge()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plan?.wakeUp?.let(TimeFormat::time) ?: stringResource(R.string.time_placeholder),
                    style = MaterialTheme.typography.displayMedium,
                    color = timeColor,
                    modifier = Modifier.weight(1f),
                )
                // 알람 켜기/끄기(③): 켜짐은 강조색, 꺼짐은 회색
                AppSwitch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle,
                    contentDescription = stringResource(R.string.alarms_toggle_description),
                )
            }
            Text(
                buildAnnotatedString {
                    append(schedule.destination.name)
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.alarms_arrival_format, TimeFormat.time(schedule.targetArrivalTime)))
                    }
                    if (plan != null) {
                        append(" | ")
                        append(stringResource(R.string.alarms_departure_format, TimeFormat.time(plan.departure)))
                    }
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                color = if (schedule.enabled) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.dp),
            )
            Box(Modifier.fillMaxWidth().padding(end = 4.dp).height(29.dp).dashedLine(colors.outline))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    !schedule.enabled -> Pill(stringResource(R.string.alarms_disabled))
                    plan == null -> Pill(stringResource(R.string.alarms_not_calculated))
                    else -> {
                        TrafficStatusChip(plan.trafficStatus)
                        Spacer(Modifier.width(10.dp))
                        val adjustment = item.adjustment
                        when {
                            adjustment?.previousWakeUp != null && adjustment.newWakeUp != null -> Text(
                                stringResource(
                                    if (adjustment.newWakeUp.isBefore(adjustment.previousWakeUp)) R.string.alarms_adjusted_earlier else R.string.alarms_adjusted_later,
                                    TimeFormat.time(adjustment.previousWakeUp),
                                    TimeFormat.time(adjustment.newWakeUp),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.danger,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            !plan.isRealtime && plan.isStale(now) -> Text(
                                stringResource(R.string.traffic_stale),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.danger,
                            )
                            else -> Text(
                                relativeUpdateText(plan.lastUpdatedAt, if (now.isAfter(plan.lastUpdatedAt)) now else plan.lastUpdatedAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 플랜 한도(⑤): 무료 플랜의 일정 사용 개수와 PRO 안내를 점선 카드로 보여준다. */
@Composable
private fun PlanLimitCard(quota: ScheduleQuota.Limited, onClick: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .dashedBorder(colors.dotted, cornerRadius = 22.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.alarms_plan_limit_format, quota.used, quota.limit),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(stringResource(R.string.alarms_plan_limit_body), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        ProBadge(dim = true)
    }
}
