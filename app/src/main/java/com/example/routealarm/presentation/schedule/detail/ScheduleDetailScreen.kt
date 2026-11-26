package com.example.routealarm.presentation.schedule.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.AppSwitch
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.ErrorState
import com.example.routealarm.core.designsystem.component.InlineAction
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.NoticeStrip
import com.example.routealarm.core.designsystem.component.Paragraph
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
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.ScheduleSound
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.policy.AlarmVolumePolicy
import com.example.routealarm.domain.policy.HolidaySkipPolicy
import com.example.routealarm.domain.policy.TravelPattern
import com.example.routealarm.domain.policy.TravelPatternPolicy
import com.example.routealarm.domain.policy.WakeUpHabit
import com.example.routealarm.presentation.common.RouteMapLines
import com.example.routealarm.presentation.common.RouteTimelineBuilder
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.WeatherStrip
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.minutesText
import com.example.routealarm.presentation.common.relativeUpdateText
import com.example.routealarm.presentation.common.rememberNow
import com.example.routealarm.presentation.common.repeatLabel
import com.example.routealarm.presentation.common.repeatText
import com.example.routealarm.presentation.home.PendingAdjustmentCard
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import com.example.routealarm.presentation.settings.AlarmSoundSheet
import com.example.routealarm.presentation.settings.FollowSettingsOption
import com.example.routealarm.presentation.settings.soundLabelRes
import com.example.routealarm.presentation.taxi.LateRiskCard
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
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
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbar.showSnackbar(resources.getString(m.messageRes()))
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
        onApplyHabit = viewModel::applyHabit,
        onDismissHabit = viewModel::dismissHabit,
        onSkipHolidays = viewModel::setSkipHolidays,
        onSound = viewModel::setSound,
        onOpenMap = { destination ->
            if (!context.openInMapApp(destination)) {
                scope.launch { snackbar.showSnackbar(resources.getString(R.string.detail_map_unavailable)) }
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
    onApplyHabit: (Int) -> Unit,
    onDismissHabit: () -> Unit,
    onOpenMap: (Place) -> Unit,
    onSkipHolidays: (Boolean) -> Unit = {},
    onSound: (ScheduleSound?) -> Unit = {},
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
                onApplyHabit = onApplyHabit,
                onDismissHabit = onDismissHabit,
                onSkipHolidays = onSkipHolidays,
                onSound = onSound,
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
    onApplyHabit: (Int) -> Unit,
    onDismissHabit: () -> Unit,
    onSkipHolidays: (Boolean) -> Unit,
    onSound: (ScheduleSound?) -> Unit,
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
            WeatherStrip(plan?.weather)
            LateRiskCard(schedule)
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
            state.wakeUpHabit?.let { habit ->
                WakeUpHabitCard(
                    habit = habit,
                    preparationMinutes = schedule.preparationMinutes,
                    onApply = { onApplyHabit(habit.extraPreparationMinutes) },
                    onDismiss = onDismissHabit,
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

            // 경로 지도(카카오맵). 좌표가 없는 예전 경로나 지도를 쓸 수 없는 기기에서는 보이지 않는다.
            val mapLines = remember(plan.route, schedule.origin, schedule.destination) {
                plan.route?.let { RouteMapLines.build(schedule.origin.point, schedule.destination.point, it.segments) }.orEmpty()
            }
            var mapFailed by remember { mutableStateOf(false) }
            if (KakaoMapSupport.isAvailable && mapLines.isNotEmpty() && !mapFailed) {
                Spacer(Modifier.height(Spacing.md))
                RouteMap(
                    lines = mapLines,
                    origin = schedule.origin.point,
                    destination = schedule.destination.point,
                    walkColor = colors.textTertiary,
                    onError = { mapFailed = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(Radius.card),
                )
            }
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

        state.travelPattern?.let { pattern ->
            Spacer(Modifier.height(Spacing.lg))
            SectionLabel(stringResource(R.string.travel_pattern_title))
            Spacer(Modifier.height(10.dp))
            TravelPatternCard(pattern)
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
            HorizontalDivider(thickness = 1.dp, color = colors.outline)
            ScheduleSoundRow(schedule, state.preferences, onSound)
            if (schedule.isRepeating) {
                HorizontalDivider(thickness = 1.dp, color = colors.outline)
                HolidaySkipRow(schedule, now, onSkipHolidays)
            }
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

/**
 * 알람 이력 기반 개인화: 평소 기상 알람을 늦게 끄면 준비 시간을 늘리자고 제안한다.
 * 적용하면 그만큼 일찍 깨우고, 넘기면 지금까지의 기록으로는 다시 묻지 않는다.
 */
@Composable
private fun WakeUpHabitCard(habit: WakeUpHabit, preparationMinutes: Int, onApply: () -> Unit, onDismiss: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth(), selected = true) {
        Column(Modifier.padding(start = 18.dp, end = 6.dp, top = 16.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.habit_title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Paragraph(
                stringResource(R.string.habit_body, habit.samples, habit.typicalLateMinutes, habit.extraPreparationMinutes),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 12.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.habit_preparation_change, preparationMinutes, preparationMinutes + habit.extraPreparationMinutes),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentText,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                InlineAction(stringResource(R.string.habit_dismiss), onClick = onDismiss)
                InlineAction(stringResource(R.string.habit_apply, habit.extraPreparationMinutes), onClick = onApply, emphasized = true)
            }
        }
    }
}


/** 반복 일정의 "공휴일에는 울리지 않기" 스위치와, 곧 건너뛸 공휴일 안내 */
@Composable
internal fun HolidaySkipRow(schedule: Schedule, now: Instant, onChange: (Boolean) -> Unit) {
    val colors = RouteAlarmTheme.colors
    val skipped = HolidaySkipPolicy.nextSkipped(schedule, now, ZoneId.systemDefault())
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!schedule.skipHolidays) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.detail_skip_holidays), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
            Text(
                when {
                    skipped != null -> stringResource(R.string.detail_skip_holidays_next, TimeFormat.dateWithWeekday(skipped.date), skipped.name)
                    schedule.skipHolidays -> stringResource(R.string.detail_skip_holidays_on)
                    else -> stringResource(R.string.detail_skip_holidays_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (skipped != null) colors.accentText else colors.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = schedule.skipHolidays, onCheckedChange = onChange)
    }
}

/** 이 일정만의 알람음·볼륨. 누르면 설정과 같은 알람음 시트를 "설정 따르기" 선택지와 함께 연다. */
@Composable
internal fun ScheduleSoundRow(schedule: Schedule, prefs: UserPreferences, onSound: (ScheduleSound?) -> Unit) {
    val colors = RouteAlarmTheme.colors
    var open by remember { mutableStateOf(false) }
    val effective = AlarmVolumePolicy.forSchedule(prefs, schedule.sound)
    val settingsSummary = soundSummary(prefs)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_alarm_sound), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
            Text(
                if (schedule.sound == null) stringResource(R.string.schedule_sound_follow_value, settingsSummary) else soundSummary(effective),
                style = MaterialTheme.typography.bodySmall,
                color = if (schedule.sound == null) colors.textSecondary else colors.accentText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(22.dp))
    }
    if (open) {
        // 설정 따르기 상태에서 소리나 볼륨을 바꾸면 설정 값을 바탕으로 이 일정 전용 값을 만든다.
        val base = schedule.sound ?: AlarmVolumePolicy.fromPreferences(prefs)
        AlarmSoundSheet(
            prefs = effective,
            title = stringResource(R.string.schedule_sound_title),
            followSettings = FollowSettingsOption(active = schedule.sound == null, summary = settingsSummary, onSelect = { onSound(null) }),
            showGradual = false,
            onSound = { onSound(base.copy(sound = it)) },
            onCustomSound = { uri, title -> onSound(base.copy(sound = AlarmSound.CUSTOM, customUri = uri, customTitle = title)) },
            onVolume = { if (it != effective.alarmVolumePercent || schedule.sound != null) onSound(base.copy(volumePercent = it)) },
            onGradual = {},
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun soundSummary(prefs: UserPreferences): String {
    val name = when (prefs.alarmSound) {
        AlarmSound.CUSTOM -> prefs.customSoundTitle ?: stringResource(R.string.settings_sound_custom)
        else -> stringResource(soundLabelRes(prefs.alarmSound))
    }
    return if (prefs.alarmSound == AlarmSound.SILENT) name else stringResource(R.string.settings_sound_value_format, name, prefs.alarmVolumePercent)
}

/**
 * 요일별 실제 이동시간(출발 직전 확인 값의 중앙값). 막대 길이는 가장 오래 걸린 요일 기준이고,
 * 평소보다 3분 이상 더 걸리는 요일은 강조색으로 칠한다.
 */
@Composable
private fun TravelPatternCard(pattern: TravelPattern) {
    val colors = RouteAlarmTheme.colors
    val longest = pattern.weekdays.maxOf { it.typicalMinutes }.coerceAtLeast(1)
    InfoCard {
        Text(
            pattern.slowest?.let { stringResource(R.string.travel_pattern_slow, dayName(it.day), it.extraMinutes) }
                ?: stringResource(R.string.travel_pattern_even, pattern.baselineMinutes),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
        )
        pattern.weekdays.forEach { day ->
            val notable = day.extraMinutes >= TravelPatternPolicy.NOTABLE_EXTRA_MINUTES
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayName(day.day), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.width(28.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(colors.dotted)) {
                    Box(
                        Modifier
                            .fillMaxWidth(day.typicalMinutes.toFloat() / longest)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (notable) colors.accent else colors.textTertiary),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.common_minutes_format, day.typicalMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (notable) colors.accentText else colors.textPrimary,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        Text(stringResource(R.string.travel_pattern_note), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
    }
}

@Composable
private fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
