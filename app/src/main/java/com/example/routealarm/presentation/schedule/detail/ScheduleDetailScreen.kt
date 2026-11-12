package com.example.routealarm.presentation.schedule.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppSwitch
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.DemoBadge
import com.example.routealarm.core.designsystem.component.ErrorState
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.NoticeStrip
import com.example.routealarm.core.designsystem.component.RouteTimelineView
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.component.TrafficStatusChip
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.presentation.common.RouteTimelineBuilder
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.minutesText
import com.example.routealarm.presentation.common.relativeUpdateText
import com.example.routealarm.presentation.common.rememberNow
import com.example.routealarm.presentation.common.repeatLabel
import com.example.routealarm.presentation.common.repeatText
import com.example.routealarm.presentation.home.PendingAdjustmentCard
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun ScheduleDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ScheduleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbar.showSnackbar(context.getString(m.messageRes()))
    }
    LaunchedEffect(state) {
        if ((state as? ScheduleDetailUiState.Success)?.deleted == true) onBack()
    }
    ScheduleDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onRefresh = viewModel::refresh,
        onToggle = viewModel::toggle,
        onDelete = viewModel::delete,
        onApplyPending = viewModel::applyPending,
        onDismissPending = viewModel::dismissPending,
        onOpenMap = { destination ->
            if (!context.openInMapApp(destination)) {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_map_unavailable)) }
            }
        },
    )
}

/**
 * 일정 상세 — UI-D-002.
 * 경로 타임라인 확인, 다시 계산, 일정 삭제, 지도 앱에서 목적지 열기.
 */
@Composable
fun ScheduleDetailScreen(
    state: ScheduleDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onApplyPending: () -> Unit,
    onDismissPending: () -> Unit,
    onOpenMap: (Place) -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    val schedule = (state as? ScheduleDetailUiState.Success)?.schedule

    Column(Modifier.fillMaxSize().background(colors.background)) {
        BackHeader(
            onBack = onBack,
            title = schedule?.title?.ifBlank { null } ?: stringResource(R.string.detail_title),
            trailing = {
                if (schedule != null) {
                    IconButton(onClick = { onEdit(schedule.id) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.detail_edit), tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                    }
                }
            },
        )
        when (state) {
            ScheduleDetailUiState.Loading -> LoadingState(Modifier.padding(top = Spacing.xxl))
            ScheduleDetailUiState.NotFound -> ErrorState(message = stringResource(R.string.error_not_found), modifier = Modifier.padding(top = Spacing.xl))
            is ScheduleDetailUiState.Success -> DetailContent(
                state = state,
                onRefresh = onRefresh,
                onToggle = onToggle,
                onApplyPending = onApplyPending,
                onDismissPending = onDismissPending,
                onOpenMap = { onOpenMap(state.schedule.destination) },
                onDelete = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.alarms_delete_confirm_title)) },
            text = { Text(stringResource(R.string.alarms_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.common_delete), color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun DetailContent(
    state: ScheduleDetailUiState.Success,
    onRefresh: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onApplyPending: () -> Unit,
    onDismissPending: () -> Unit,
    onOpenMap: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    val schedule = state.schedule
    val plan = schedule.plan
    val now by rememberNow()
    val timeColor by animateColorAsState(if (schedule.enabled) colors.textPrimary else colors.textTertiary, label = "detailTime")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(repeatLabel(schedule.repeatDays, schedule.oneTimeDate), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            if (schedule.isDemo) {
                Spacer(Modifier.width(8.dp))
                DemoBadge()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                plan?.wakeUp?.let(TimeFormat::time) ?: stringResource(R.string.time_placeholder),
                style = MaterialTheme.typography.displayLarge,
                color = timeColor,
                modifier = Modifier.weight(1f),
            )
            AppSwitch(checked = schedule.enabled, onCheckedChange = onToggle, contentDescription = stringResource(R.string.alarms_toggle_description))
        }
        Text(
            if (plan != null) {
                stringResource(
                    R.string.detail_summary_format,
                    schedule.destination.name,
                    TimeFormat.time(schedule.targetArrivalTime),
                    TimeFormat.time(plan.departure),
                )
            } else {
                stringResource(R.string.home_summary_no_date_format, schedule.destination.name, TimeFormat.time(schedule.targetArrivalTime))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (plan != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrafficStatusChip(plan.trafficStatus)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        state.isRefreshing -> stringResource(R.string.home_refreshing)
                        !plan.isRealtime && plan.isStale(now) -> stringResource(R.string.traffic_stale)
                        else -> relativeUpdateText(plan.lastUpdatedAt, if (now.isAfter(plan.lastUpdatedAt)) now else plan.lastUpdatedAt)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary,
                )
            }
        }

        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!schedule.enabled) NoticeStrip(text = stringResource(R.string.detail_disabled_hint))
            val refreshError = state.refreshError
            when {
                refreshError != null -> NoticeStrip(
                    text = stringResource(refreshError.messageRes()) + " " + stringResource(R.string.detail_refresh_failed_suffix),
                    icon = Icons.Outlined.CloudOff,
                )
                // 저장해 둔 경로로 계산한 상태(SFR-009 오프라인 처리)
                plan != null && !plan.isRealtime && !plan.isStale(now) ->
                    NoticeStrip(text = stringResource(R.string.home_offline_banner), icon = Icons.Outlined.CloudOff)
            }
            schedule.pendingAdjustment?.let { pending ->
                PendingAdjustmentCard(
                    kind = pending.kind,
                    deltaMinutes = pending.deltaMinutes,
                    from = plan?.wakeUp?.let(TimeFormat::time).orEmpty(),
                    to = TimeFormat.time(pending.proposedPlan.wakeUp),
                    onApply = onApplyPending,
                    onDismiss = onDismissPending,
                    basedOnCache = !pending.proposedPlan.isRealtime,
                )
            }
        }

        if (plan != null) {
            // 경로 타임라인
            Spacer(Modifier.height(Spacing.lg))
            SectionLabel(stringResource(R.string.home_route_title))
            Spacer(Modifier.height(14.dp))
            val originName = if (schedule.useCurrentLocation) stringResource(R.string.create_current_location_name) else schedule.origin.name
            val timeline = remember(plan, originName, schedule.destination.name) {
                RouteTimelineBuilder.build(plan, originName, schedule.destination.name)
            }
            RouteTimelineView(timeline, slackMinutes = plan.slackMinutes)
        }

        // 다시 계산 · 지도 앱 열기
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = stringResource(if (state.isRefreshing) R.string.home_refreshing else R.string.detail_recalculate),
                onClick = onRefresh,
                enabled = schedule.enabled && !state.isRefreshing,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(text = stringResource(R.string.detail_open_map), onClick = onOpenMap, modifier = Modifier.weight(1f))
        }

        if (plan != null) {
            Spacer(Modifier.height(Spacing.lg))
            SectionLabel(stringResource(R.string.detail_timeline_title))
            Spacer(Modifier.height(10.dp))
            val preparation = minutesBetween(plan.wakeUp, plan.departure)
            val travel = minutesBetween(plan.departure, plan.estimatedArrival)
            val slack = minutesBetween(plan.estimatedArrival, plan.targetArrival)
            InfoCard {
                InfoRow(stringResource(R.string.create_preparation_label), stringResource(R.string.common_minutes_format, preparation))
                InfoRow(stringResource(R.string.create_travel_label), stringResource(R.string.common_minutes_format, travel))
                InfoRow(stringResource(R.string.create_buffer_label), stringResource(R.string.common_minutes_format, slack))
                HorizontalDivider(thickness = 1.dp, color = colors.outline)
                InfoRow(
                    stringResource(R.string.create_result_total, TimeFormat.time(plan.targetArrival)),
                    stringResource(R.string.common_minutes_format, preparation + travel + slack),
                    emphasized = true,
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        SectionLabel(stringResource(R.string.detail_section_settings))
        Spacer(Modifier.height(10.dp))
        InfoCard {
            InfoRow(stringResource(R.string.detail_origin_label), if (schedule.useCurrentLocation) stringResource(R.string.create_current_location_name) else schedule.origin.name)
            InfoRow(stringResource(R.string.detail_destination_label), schedule.destination.name)
            InfoRow(stringResource(R.string.detail_repeat_label), repeatText(schedule.repeatDays, schedule.oneTimeDate))
            InfoRow(
                stringResource(R.string.create_alarm_mode_label),
                stringResource(
                    if (schedule.alarmMode == AlarmMode.WAKE_UP_ONLY) R.string.create_alarm_mode_wake_only else R.string.create_alarm_mode_wake_and_departure,
                ),
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        SectionLabel(stringResource(R.string.detail_history_title))
        Spacer(Modifier.height(10.dp))
        if (state.history.isEmpty()) {
            Text(stringResource(R.string.detail_history_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        } else {
            InfoCard {
                state.history.forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.outline)
                    HistoryRow(event)
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        SecondaryButton(text = stringResource(R.string.detail_delete), onClick = onDelete, danger = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String, emphasized: Boolean = false) {
    val colors = RouteAlarmTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (emphasized) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = if (emphasized) colors.textPrimary else colors.textSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) colors.accentText else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HistoryRow(event: AlarmHistoryEvent) {
    val colors = RouteAlarmTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(historyLabel(event.type)), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            val change = if (event.previousWakeUp != null && event.newWakeUp != null && event.previousWakeUp != event.newWakeUp) {
                stringResource(R.string.history_wake_change_format, TimeFormat.time(event.previousWakeUp), TimeFormat.time(event.newWakeUp))
            } else {
                event.travelMinutes?.let { stringResource(R.string.history_travel_format, minutesText(it)) }
            }
            if (change != null) Text(change, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Text(TimeFormat.dateTime(event.occurredAt), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
    }
}

private fun historyLabel(type: AlarmEventType) = when (type) {
    AlarmEventType.CREATED -> R.string.history_created
    AlarmEventType.RECALCULATED -> R.string.history_recalculated
    AlarmEventType.AUTO_ADJUSTED -> R.string.history_auto_adjusted
    AlarmEventType.ADJUSTMENT_SUGGESTED -> R.string.history_adjustment_suggested
    AlarmEventType.ADJUSTMENT_APPLIED -> R.string.history_adjustment_applied
    AlarmEventType.FIRED -> R.string.history_fired
    AlarmEventType.SNOOZED -> R.string.history_snoozed
    AlarmEventType.DISMISSED -> R.string.history_dismissed
    AlarmEventType.REFRESH_FAILED -> R.string.history_refresh_failed
}

private fun minutesBetween(from: Instant, to: Instant): Int = Duration.between(from, to).toMinutes().coerceAtLeast(0).toInt()

/**
 * 기기의 지도 앱에서 목적지를 연다(SFR-012). 좌표는 사용자가 버튼을 눌렀을 때만 지도 앱으로 전달된다.
 * @return 지도 앱이 없어 열지 못했으면 false
 */
private fun Context.openInMapApp(place: Place): Boolean {
    val label = Uri.encode(place.name)
    val uri = "geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}($label)".toUri()
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
