package com.example.routealarm.presentation.alarmlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppSwitch
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.ProBadge
import com.example.routealarm.core.designsystem.component.ScreenHeader
import com.example.routealarm.core.designsystem.component.dashedBorder
import com.example.routealarm.core.designsystem.theme.AppType
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.ScheduleQuota
import com.example.routealarm.domain.model.TrafficStatus
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.relativeUpdateText
import com.example.routealarm.presentation.common.rememberNow
import com.example.routealarm.presentation.common.repeatLabel
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import java.time.Instant
import java.time.ZoneId

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
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AlarmListEvent.OpenCreate -> onCreateSchedule()
                AlarmListEvent.ShowPaywall -> onShowPaywall()
                is AlarmListEvent.ShowLimitMessage -> {
                    val result = snackbar.showSnackbar(
                        message = resources.getString(R.string.alarms_limit_message, event.limit),
                        actionLabel = resources.getString(R.string.alarms_limit_action),
                    )
                    if (result == SnackbarResult.ActionPerformed) onShowPaywall()
                }
                is AlarmListEvent.ShowError -> snackbar.showSnackbar(resources.getString(event.error.messageRes()))
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
                    contentPadding = PaddingValues(top = 8.dp, bottom = Spacing.lg),
                ) {
                    item(key = "top-divider") {
                        HorizontalDivider(Modifier.padding(start = Spacing.screen), thickness = 1.dp, color = RouteAlarmTheme.colors.outline)
                    }
                    items(state.items, key = { it.schedule.id }) { item ->
                        AlarmRow(
                            item = item,
                            now = now,
                            onClick = { onOpenSchedule(item.schedule.id) },
                            onToggle = { onToggle(item.schedule.id, it) },
                        )
                    }
                    (state.quota as? ScheduleQuota.Limited)?.let { quota ->
                        item(key = "plan-limit") {
                            Box(Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg)) {
                                PlanLimitCard(quota, onClick = onShowPaywall)
                            }
                        }
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
 * 알람 행(②): 아이폰 시계 앱처럼 구분선으로 나눈 목록. 크고 가는 "오전 7:29", 그 아래 반복 요일과 도착지,
 * 맨 아래 교통 정보 한 줄. 누르면 일정 상세로 간다. 꺼진 알람은 전체를 흐리게 그린다.
 * 교통 변화로 조정된 일정은 "변경 전 → 변경 후" 시각을 강조색으로 보여준다(④).
 */
@Composable
private fun AlarmRow(item: AlarmListItem, now: Instant, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = RouteAlarmTheme.colors
    val schedule = item.schedule
    val plan = schedule.plan
    val enabled = schedule.enabled
    val timeColor by animateColorAsState(if (enabled) colors.textPrimary else colors.textTertiary, label = "alarmTime")
    val subColor = if (enabled) colors.textSecondary else colors.textTertiary

    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val wakeUp = plan?.wakeUp?.atZone(ZoneId.systemDefault())?.toLocalTime()
                Row(verticalAlignment = Alignment.Bottom) {
                    if (wakeUp != null) {
                        Text(
                            stringResource(if (wakeUp.hour < NOON) R.string.time_am else R.string.time_pm),
                            style = AppType.ListMeridiem,
                            color = timeColor,
                            modifier = Modifier.padding(bottom = 5.dp, end = 4.dp),
                        )
                    }
                    Text(
                        wakeUp?.let { "${(it.hour % NOON).let { h -> if (h == 0) NOON else h }}:${"%02d".format(it.minute)}" }
                            ?: stringResource(R.string.time_placeholder),
                        style = AppType.ListTime,
                        color = timeColor,
                    )
                }
                Text(
                    stringResource(
                        R.string.alarms_row_label,
                        repeatLabel(schedule.repeatDays, schedule.oneTimeDate),
                        schedule.destination.name,
                        TimeFormat.time(schedule.targetArrivalTime),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                TrafficLine(item, now)
            }
            Spacer(Modifier.width(12.dp))
            // 알람 켜기/끄기(③): 켜짐은 강조색, 꺼짐은 회색
            AppSwitch(
                checked = enabled,
                onCheckedChange = onToggle,
                contentDescription = stringResource(R.string.alarms_toggle_description),
            )
        }
        HorizontalDivider(Modifier.padding(start = Spacing.screen), thickness = 1.dp, color = colors.outline)
    }
}

/** 알람 행 맨 아래 교통 정보: 상태 점 + "교통 정상 · 07:59 출발 · 2분 전 갱신" 처럼 한 줄 */
@Composable
private fun TrafficLine(item: AlarmListItem, now: Instant) {
    val colors = RouteAlarmTheme.colors
    val schedule = item.schedule
    val plan = schedule.plan
    val (dotColor, text) = when {
        !schedule.enabled -> colors.textTertiary to stringResource(R.string.alarms_disabled)
        plan == null -> colors.textTertiary to stringResource(R.string.alarms_not_calculated)
        else -> {
            val status = plan.trafficStatus
            val statusText = when (status) {
                TrafficStatus.Normal -> stringResource(R.string.traffic_normal)
                is TrafficStatus.MinorDelay -> stringResource(R.string.traffic_minor_delay, status.delayMinutes)
                is TrafficStatus.SevereDelay -> stringResource(R.string.traffic_severe_delay, status.delayMinutes)
            }
            val adjustment = item.adjustment
            val tail = when {
                adjustment?.previousWakeUp != null && adjustment.newWakeUp != null -> stringResource(
                    if (adjustment.newWakeUp.isBefore(adjustment.previousWakeUp)) R.string.alarms_adjusted_earlier else R.string.alarms_adjusted_later,
                    TimeFormat.time(adjustment.previousWakeUp),
                    TimeFormat.time(adjustment.newWakeUp),
                )
                !plan.isRealtime && plan.isStale(now) -> stringResource(R.string.traffic_stale)
                else -> relativeUpdateText(plan.lastUpdatedAt, if (now.isAfter(plan.lastUpdatedAt)) now else plan.lastUpdatedAt)
            }
            val alert = status !is TrafficStatus.Normal || adjustment?.newWakeUp != null || (!plan.isRealtime && plan.isStale(now))
            (if (alert) colors.danger else colors.accent) to stringResource(
                R.string.alarms_traffic_line, statusText, TimeFormat.time(plan.departure), tail,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val NOON = 12

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
