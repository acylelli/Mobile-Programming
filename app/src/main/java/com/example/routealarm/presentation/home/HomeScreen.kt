package com.example.routealarm.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.DemoBadge
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.LabeledValue
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.SkeletonBlock
import com.example.routealarm.core.designsystem.component.StatusChip
import com.example.routealarm.core.designsystem.component.TrafficStatusChip
import com.example.routealarm.core.designsystem.component.TransitTimeline
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AdjustmentKind
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.minutesText
import com.example.routealarm.presentation.common.relativeDayText
import kotlin.math.abs

@Composable
fun HomeRoute(
    onCreateSchedule: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(event) {
        val e = event ?: return@LaunchedEffect
        if (e is HomeEvent.ShowMessage) snackbar.showSnackbar(context.getString(e.error.messageRes()))
        viewModel.consumeEvent()
    }

    HomeScreen(
        state = state,
        snackbarHostState = snackbar,
        onCreateSchedule = onCreateSchedule,
        onOpenSchedule = onOpenSchedule,
        onRefresh = viewModel::refresh,
        onToggle = viewModel::toggle,
        onApplyPending = viewModel::applyPending,
        onDismissPending = viewModel::dismissPending,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onCreateSchedule: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onRefresh: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onApplyPending: (Long) -> Unit,
    onDismissPending: (Long) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateSchedule,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_new_alarm)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { Greeting() }
            when (state) {
                HomeUiState.Loading -> item { PrimaryCardSkeleton() }
                HomeUiState.Empty -> item {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        action = { PrimaryButton(text = stringResource(R.string.home_new_alarm), onClick = onCreateSchedule) },
                    )
                }
                is HomeUiState.Success -> {
                    item {
                        PrimaryAlarmCard(
                            schedule = state.primary,
                            isRefreshing = state.isRefreshing,
                            isStale = state.isStale,
                            onClick = { onOpenSchedule(state.primary.id) },
                            onRefresh = { onRefresh(state.primary.id) },
                            onToggle = { onToggle(state.primary.id, it) },
                        )
                    }
                    if (state.refreshError != null) item { OfflineBanner() }
                    if (state.pendingOutdatedMessage) item { InfoLine(stringResource(R.string.home_pending_outdated)) }
                    state.primary.pendingAdjustment?.let { pending ->
                        item {
                            PendingAdjustmentCard(
                                kind = pending.kind,
                                deltaMinutes = pending.deltaMinutes,
                                from = state.primary.plan?.wakeUp?.let(TimeFormat::time).orEmpty(),
                                to = TimeFormat.time(pending.proposedPlan.wakeUp),
                                onApply = { onApplyPending(state.primary.id) },
                                onDismiss = { onDismissPending(state.primary.id) },
                            )
                        }
                    }
                    state.primary.plan?.route?.let { route ->
                        item { RouteCard(route = route) }
                    }
                    if (state.upcoming.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.home_upcoming_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                        items(state.upcoming, key = { it.id }) { schedule ->
                            UpcomingRow(schedule = schedule, onClick = { onOpenSchedule(schedule.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Greeting() {
    Column {
        Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 홈의 핵심 카드. 기상 시각을 화면에서 가장 크게 보여주고 출발/도착/이동/여유/교통 상태를 한눈에 제공한다.
 */
@Composable
fun PrimaryAlarmCard(
    schedule: Schedule,
    isRefreshing: Boolean,
    isStale: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val plan = schedule.plan
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.home_day_destination_format,
                        plan?.wakeUp?.let { relativeDayText(it) } ?: "",
                        schedule.destination.name,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (schedule.isDemo) DemoBadge(Modifier.padding(end = Spacing.sm))
                val toggleDescription = stringResource(R.string.alarms_toggle_description)
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = toggleDescription },
                )
            }
            Spacer(Modifier.height(Spacing.md))
            // 시각이 바뀌면 숫자가 부드럽게 교체된다.
            AnimatedContent(
                targetState = plan?.wakeUp?.let(TimeFormat::time) ?: "--:--",
                transitionSpec = { (fadeIn() + slideInVertically { it / 4 }) togetherWith (fadeOut() + slideOutVertically { -it / 4 }) },
                label = "wakeUpTime",
            ) { time ->
                Text(
                    time,
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (schedule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.home_wake_up_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (plan != null) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    stringResource(R.string.home_departure_arrival_format, TimeFormat.time(plan.departure), TimeFormat.time(plan.estimatedArrival)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.home_travel_format, minutesText(plan.travelMinutes)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.home_slack_format, plan.slackMinutes), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrafficStatusChip(plan.trafficStatus)
                    plan.travelChangeMinutes?.takeIf { it != 0 }?.let { change ->
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            if (change > 0) stringResource(R.string.traffic_change_increase, change)
                            else stringResource(R.string.traffic_change_decrease, abs(change)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (!plan.isRealtime) {
                        StatusChip(
                            text = stringResource(R.string.traffic_cached),
                            dotColor = MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !isRefreshing && schedule.enabled) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.home_refresh))
                    }
                }
                if (isRefreshing) InfoLine(stringResource(R.string.home_refreshing))
                if (isStale) InfoLine(stringResource(R.string.home_stale_warning, TimeFormat.dateTime(plan.lastUpdatedAt)), isWarning = true)
            }
            if (schedule.isDemo && !schedule.enabled) InfoLine(stringResource(R.string.home_demo_hint))
        }
    }
}

@Composable
private fun InfoLine(text: String, isWarning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isWarning) RouteAlarmTheme.appColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun OfflineBanner() {
    AppCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.md))
            Text(stringResource(R.string.home_offline_banner), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PendingAdjustmentCard(
    kind: AdjustmentKind,
    deltaMinutes: Long,
    from: String,
    to: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val magnitude = abs(deltaMinutes)
    val body = when {
        kind == AdjustmentKind.NEEDS_CONFIRMATION -> stringResource(R.string.home_pending_confirm, magnitude, from, to)
        deltaMinutes < 0 -> stringResource(R.string.home_pending_earlier, magnitude, from, to)
        else -> stringResource(R.string.home_pending_later, magnitude, from, to)
    }
    AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(Spacing.md)) {
            Text(stringResource(R.string.home_pending_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(Spacing.xs))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_pending_dismiss)) }
                TextButton(onClick = onApply) { Text(stringResource(R.string.home_pending_apply), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** 접었다 펼 수 있는 경로 카드 */
@Composable
fun RouteCard(route: com.example.routealarm.domain.model.TransitRoute, initiallyExpanded: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    AppCard(onClick = { expanded = !expanded }) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_route_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.common_more),
                )
            }
            com.example.routealarm.core.designsystem.component.RouteSummaryText(route)
            AnimatedVisibility(visible = expanded) {
                TransitTimeline(route, Modifier.padding(top = Spacing.md))
            }
        }
    }
}

@Composable
private fun UpcomingRow(schedule: Schedule, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            LabeledValue(
                label = schedule.plan?.wakeUp?.let { relativeDayText(it) } ?: "",
                value = schedule.plan?.wakeUp?.let(TimeFormat::time) ?: "--:--",
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(schedule.destination.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.home_target_format, TimeFormat.time(schedule.targetArrivalTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!schedule.enabled) {
                Text(stringResource(R.string.alarms_disabled), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrimaryCardSkeleton() {
    AppCard {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SkeletonBlock(Modifier.width(160.dp))
            SkeletonBlock(Modifier.fillMaxWidth(), height = 72.dp)
            SkeletonBlock(Modifier.width(220.dp))
            SkeletonBlock(Modifier.width(120.dp))
        }
    }
}
