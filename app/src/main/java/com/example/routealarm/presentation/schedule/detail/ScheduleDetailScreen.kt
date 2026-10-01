package com.example.routealarm.presentation.schedule.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.ErrorState
import com.example.routealarm.core.designsystem.component.LabeledValue
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.minutesText
import com.example.routealarm.presentation.common.repeatText
import com.example.routealarm.presentation.home.PendingAdjustmentCard
import com.example.routealarm.presentation.home.PrimaryAlarmCard
import com.example.routealarm.presentation.home.RouteCard

@Composable
fun ScheduleDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ScheduleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(m.messageRes()))
        viewModel.consumeMessage()
    }
    LaunchedEffect(state) {
        if ((state as? ScheduleDetailUiState.Success)?.deleted == true) onBack()
    }
    ScheduleDetailScreen(
        state = state,
        snackbarHostState = snackbar,
        onBack = onBack,
        onEdit = onEdit,
        onRefresh = viewModel::refresh,
        onToggle = viewModel::toggle,
        onDelete = viewModel::delete,
        onApplyPending = viewModel::applyPending,
        onDismissPending = viewModel::dismissPending,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDetailScreen(
    state: ScheduleDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onApplyPending: () -> Unit,
    onDismissPending: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val schedule = (state as? ScheduleDetailUiState.Success)?.schedule

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(schedule?.title ?: stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                actions = {
                    if (schedule != null) {
                        IconButton(onClick = { onEdit(schedule.id) }) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.detail_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.detail_delete)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (state) {
            ScheduleDetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            ScheduleDetailUiState.NotFound -> ErrorState(message = stringResource(R.string.error_not_found), modifier = Modifier.padding(padding))
            is ScheduleDetailUiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    PrimaryAlarmCard(
                        schedule = state.schedule,
                        isRefreshing = state.isRefreshing,
                        isStale = state.schedule.plan?.let { !it.isRealtime && it.isStale(java.time.Instant.now()) } ?: false,
                        onClick = {},
                        onRefresh = onRefresh,
                        onToggle = onToggle,
                    )
                }
                if (!state.schedule.enabled) {
                    item { Text(stringResource(R.string.detail_disabled_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                state.refreshError?.let { error ->
                    item { Text(stringResource(error.messageRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                state.schedule.pendingAdjustment?.let { pending ->
                    item {
                        PendingAdjustmentCard(
                            kind = pending.kind,
                            deltaMinutes = pending.deltaMinutes,
                            from = state.schedule.plan?.wakeUp?.let(TimeFormat::time).orEmpty(),
                            to = TimeFormat.time(pending.proposedPlan.wakeUp),
                            onApply = onApplyPending,
                            onDismiss = onDismissPending,
                        )
                    }
                }
                state.schedule.plan?.let { plan ->
                    item { CalculationCard(state.schedule) }
                    plan.route?.let { item { RouteCard(route = it, initiallyExpanded = true) } }
                }
                item { SettingsCard(state.schedule) }
                item {
                    Text(stringResource(R.string.detail_history_title), style = MaterialTheme.typography.titleMedium)
                }
                if (state.history.isEmpty()) {
                    item { Text(stringResource(R.string.detail_history_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item {
                        AppCard {
                            Column(Modifier.padding(Spacing.md)) {
                                state.history.forEachIndexed { index, event ->
                                    HistoryRow(event)
                                    if (index != state.history.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.alarms_delete_confirm_title)) },
            text = { Text(stringResource(R.string.alarms_delete_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun CalculationCard(schedule: Schedule) {
    val plan = schedule.plan ?: return
    AppCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.detail_timeline_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue(stringResource(R.string.create_result_target), TimeFormat.time(plan.targetArrival))
                LabeledValue(stringResource(R.string.create_result_arrival), TimeFormat.time(plan.estimatedArrival), horizontalAlignment = Alignment.CenterHorizontally)
                LabeledValue(stringResource(R.string.create_result_departure), TimeFormat.time(plan.departure), horizontalAlignment = Alignment.CenterHorizontally)
                LabeledValue(stringResource(R.string.create_result_wake_up), TimeFormat.time(plan.wakeUp), horizontalAlignment = Alignment.End)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabeledValue(stringResource(R.string.create_preparation_label), minutesText(schedule.preparationMinutes))
                LabeledValue(stringResource(R.string.create_travel_label), minutesText(plan.travelMinutes), horizontalAlignment = Alignment.CenterHorizontally)
                LabeledValue(stringResource(R.string.create_buffer_label), minutesText(plan.safetyBufferMinutes), horizontalAlignment = Alignment.End)
            }
            Text(
                stringResource(R.string.detail_last_updated, TimeFormat.dateTime(plan.lastUpdatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(schedule: Schedule) {
    AppCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.detail_section_settings), style = MaterialTheme.typography.titleMedium)
            LabeledValue(stringResource(R.string.detail_origin_label), schedule.origin.name)
            LabeledValue(stringResource(R.string.detail_destination_label), schedule.destination.name)
            LabeledValue(stringResource(R.string.detail_repeat_label), repeatText(schedule.repeatDays, schedule.oneTimeDate))
            LabeledValue(stringResource(R.string.create_alarm_mode_label), stringResource(
                if (schedule.alarmMode == com.example.routealarm.domain.model.AlarmMode.WAKE_UP_ONLY) R.string.create_alarm_mode_wake_only
                else R.string.create_alarm_mode_wake_and_departure,
            ))
        }
    }
}

@Composable
private fun HistoryRow(event: AlarmHistoryEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(historyLabel(event.type)), style = MaterialTheme.typography.bodyMedium)
            val change = if (event.previousWakeUp != null && event.newWakeUp != null && event.previousWakeUp != event.newWakeUp) {
                stringResource(R.string.history_wake_change_format, TimeFormat.time(event.previousWakeUp), TimeFormat.time(event.newWakeUp))
            } else {
                event.travelMinutes?.let { stringResource(R.string.home_travel_format, minutesText(it)) }
            }
            if (change != null) Text(change, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(0.dp))
        Text(TimeFormat.dateTime(event.occurredAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
