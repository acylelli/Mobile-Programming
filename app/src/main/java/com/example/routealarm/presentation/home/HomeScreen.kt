package com.example.routealarm.presentation.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.DemoBadge
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.InlineAction
import com.example.routealarm.core.designsystem.component.NoticeStrip
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.Pill
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.RouteTimelineView
import com.example.routealarm.core.designsystem.component.ScreenHeader
import com.example.routealarm.core.designsystem.component.SkeletonBlock
import com.example.routealarm.core.designsystem.component.TagBadge
import com.example.routealarm.core.designsystem.component.TrafficStatusChip
import com.example.routealarm.core.designsystem.theme.AppType
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AdjustmentKind
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.presentation.common.RouteTimelineBuilder
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.dayPartTitle
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.relativeUpdateText
import com.example.routealarm.presentation.common.rememberNow
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import com.example.routealarm.presentation.permission.rememberPermissionStatus
import java.time.Instant
import kotlin.math.abs

/** 타임라인의 글자 열이 시작하는 위치(시각 열 64 + 레일 56). 보조 문구를 이 열에 맞춘다. */
private val TimelineTextInset = 120.dp

@Composable
fun HomeRoute(
    onCreateSchedule: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onShowPaywall: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.ShowMessage -> snackbar.showSnackbar(context.getString(event.error.messageRes()))
            }
        }
    }

    HomeScreen(
        state = state,
        onCreateSchedule = onCreateSchedule,
        onOpenSchedule = onOpenSchedule,
        onShowPaywall = onShowPaywall,
        onRefresh = viewModel::refresh,
        onApplyPending = viewModel::applyPending,
        onDismissPending = viewModel::dismissPending,
    )
}

/**
 * 홈 (내일 경로) — UI-B-001.
 * 가장 가까운 일정 하나를 기상부터 도착까지의 노선도로 보여준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onCreateSchedule: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onShowPaywall: () -> Unit,
    onRefresh: (Long) -> Unit,
    onApplyPending: (Long) -> Unit,
    onDismissPending: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(RouteAlarmTheme.colors.background)) {
        when (state) {
            HomeUiState.Loading -> HomeSkeleton()

            HomeUiState.Empty -> {
                ScreenHeader(stringResource(R.string.home_title_fallback))
                Spacer(Modifier.height(Spacing.xl))
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                    action = { PrimaryButton(text = stringResource(R.string.home_new_schedule), onClick = onCreateSchedule) },
                )
            }

            is HomeUiState.Success -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onRefresh(state.schedule.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        HomeContent(
                            state = state,
                            onOpenSchedule = { onOpenSchedule(state.schedule.id) },
                            onRefresh = { onRefresh(state.schedule.id) },
                            onApplyPending = { onApplyPending(state.schedule.id) },
                            onDismissPending = { onDismissPending(state.schedule.id) },
                        )
                    }
                }
                // PRO 안내 띠(⑥): 무료 플랜에서만, 스크롤과 무관하게 탭 바로 위에 고정한다.
                if (!state.isPro) {
                    ProStrip(
                        onClick = onShowPaywall,
                        modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onOpenSchedule: () -> Unit,
    onRefresh: () -> Unit,
    onApplyPending: () -> Unit,
    onDismissPending: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    val context = LocalContext.current
    val schedule = state.schedule
    val plan = schedule.plan
    val now by rememberNow()
    val permissions = rememberPermissionStatus()
    // 꺼 둔 일정은 다음 회차로 넘기지 않으므로 계획이 지난 날짜일 수 있다. 그럴 땐 날짜 표현을 뺀다.
    val planIsUpcoming = plan != null && plan.targetArrival.isAfter(now)

    ScreenHeader(
        title = if (plan != null && planIsUpcoming) dayPartTitle(plan.wakeUp) else stringResource(R.string.home_title_fallback),
        trailing = {
            if (plan != null) {
                // 교통 상태(②). 누르면 교통 정보를 지금 다시 확인한다.
                val refreshLabel = stringResource(R.string.home_refresh)
                Row(
                    Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !state.isRefreshing, onClickLabel = refreshLabel, role = Role.Button, onClick = onRefresh),
                ) {
                    if (state.isRefreshing) {
                        Pill(stringResource(R.string.home_refreshing), contentColor = colors.textPrimary)
                    } else {
                        TrafficStatusChip(plan.trafficStatus)
                    }
                }
            }
        },
    )

    // 다음 일정 요약(①): 날짜, 목적지, 목표 도착 시각
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            scheduleSummary(schedule, plan.takeIf { planIsUpcoming }),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (schedule.isDemo) {
            Spacer(Modifier.width(8.dp))
            DemoBadge()
        }
    }
    plan?.travelChangeMinutes?.takeIf { it != 0 }?.let { change ->
        Text(
            if (change > 0) stringResource(R.string.traffic_change_increase, change) else stringResource(R.string.traffic_change_decrease, abs(change)),
            style = MaterialTheme.typography.bodySmall,
            color = if (change > 0) colors.danger else colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = 2.dp),
        )
    }

    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!permissions.essentialGranted) {
            NoticeStrip(
                title = stringResource(R.string.permission_needed),
                text = stringResource(R.string.permission_needed_body),
                icon = Icons.Outlined.NotificationsOff,
                danger = true,
                action = { InlineAction(stringResource(R.string.permission_open_settings), onClick = { context.openAppSettings() }, emphasized = true) },
            )
        }
        when {
            state.refreshError != null -> NoticeStrip(text = stringResource(R.string.home_offline_banner), icon = Icons.Outlined.CloudOff)
            state.isStale && plan != null -> NoticeStrip(
                title = stringResource(R.string.traffic_stale),
                text = stringResource(R.string.home_stale_warning, TimeFormat.dateTime(plan.lastUpdatedAt)),
                icon = Icons.Outlined.History,
            )
            // 실시간 조회에 실패해 저장해 둔 경로로 계산한 상태도 같은 안내를 보여준다(SFR-009 오프라인 처리).
            plan?.isRealtime == false -> NoticeStrip(text = stringResource(R.string.home_offline_banner), icon = Icons.Outlined.CloudOff)
        }
        if (state.pendingOutdatedMessage) NoticeStrip(text = stringResource(R.string.home_pending_outdated))
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
        if (!schedule.enabled) {
            NoticeStrip(text = stringResource(if (schedule.isDemo) R.string.home_demo_hint else R.string.home_disabled_hint))
        }
    }

    if (plan != null) {
        val originName = if (schedule.useCurrentLocation) stringResource(R.string.create_current_location_name) else schedule.origin.name
        val timeline = remember(plan, originName, schedule.destination.name) {
            RouteTimelineBuilder.build(plan, originName, schedule.destination.name)
        }
        val detailLabel = stringResource(R.string.detail_title)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable(onClickLabel = detailLabel, onClick = onOpenSchedule)
                .padding(start = Spacing.screen, end = Spacing.screen, top = 12.dp, bottom = 8.dp),
        ) {
            RouteTimelineView(timeline, slackMinutes = plan.slackMinutes)
            Spacer(Modifier.height(14.dp))
            // 데이터 출처와 마지막 갱신 시각(SFR-011)
            Text(
                stringResource(
                    R.string.home_source_format,
                    stringResource(dataSourceRes(state.isDemoData, plan)),
                    relativeUpdateText(plan.lastUpdatedAt, laterOf(now, plan.lastUpdatedAt)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = TimelineTextInset),
            )
        }
    }
    Spacer(Modifier.height(Spacing.md))
}

private fun laterOf(a: Instant, b: Instant): Instant = if (a.isAfter(b)) a else b

private fun dataSourceRes(isDemoData: Boolean, plan: AlarmPlan): Int = when {
    !plan.isRealtime -> R.string.traffic_cached
    isDemoData -> R.string.home_source_demo
    else -> R.string.home_source_realtime
}

@Composable
private fun scheduleSummary(schedule: Schedule, upcomingPlan: AlarmPlan?): String {
    val target = TimeFormat.time(schedule.targetArrivalTime)
    return if (upcomingPlan != null) {
        stringResource(R.string.home_summary_format, TimeFormat.dateWithWeekday(upcomingPlan.targetArrival), schedule.destination.name, target)
    } else {
        stringResource(R.string.home_summary_no_date_format, schedule.destination.name, target)
    }
}

/**
 * 자동 조정이 꺼져 있거나(무료 플랜 포함) 변화가 너무 커서 사용자 확인이 필요한 변경안.
 * 변경 전 → 변경 후 시각을 크게 보여주고 [변경하기]로 바로 적용한다(SFR-010).
 */
@Composable
fun PendingAdjustmentCard(
    kind: AdjustmentKind,
    deltaMinutes: Long,
    from: String,
    to: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 실시간 조회에 실패해 저장해 둔 경로에 여유를 더한 변경안인지 */
    basedOnCache: Boolean = false,
) {
    val colors = RouteAlarmTheme.colors
    val magnitude = abs(deltaMinutes).toInt()
    val body = when {
        basedOnCache && deltaMinutes < 0 -> stringResource(R.string.home_pending_cached, magnitude)
        kind == AdjustmentKind.NEEDS_CONFIRMATION -> stringResource(R.string.home_pending_confirm, magnitude)
        deltaMinutes < 0 -> stringResource(R.string.home_pending_earlier, magnitude)
        else -> stringResource(R.string.home_pending_later, magnitude)
    }
    AppCard(modifier = modifier.fillMaxWidth(), selected = true) {
        Column(Modifier.padding(start = 18.dp, end = 6.dp, top = 16.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.home_pending_title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(from, style = AppType.Figure.copy(textDecoration = TextDecoration.LineThrough), color = colors.textTertiary)
                Text("→", style = MaterialTheme.typography.titleMedium, color = colors.textTertiary)
                Text(to, style = AppType.Figure, color = colors.accentText)
            }
            Spacer(Modifier.height(4.dp))
            Paragraph(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                InlineAction(stringResource(R.string.home_pending_dismiss), onClick = onDismiss)
                InlineAction(stringResource(R.string.home_pending_apply), onClick = onApply, emphasized = true)
            }
        }
    }
}

/** PRO 안내 띠(⑥): 무료 플랜의 한도와 PRO 혜택. 누르면 PRO 페이월이 열린다. */
@Composable
private fun ProStrip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.group)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagBadge(
            stringResource(R.string.plan_free_short),
            containerColor = colors.background,
            contentColor = colors.textPrimary,
            modifier = Modifier.border(1.dp, colors.outline, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_pro_strip_title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.home_pro_strip_body), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.padding(horizontal = Spacing.screen, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonBlock(Modifier.width(150.dp), height = 34.dp)
        SkeletonBlock(Modifier.width(230.dp), height = 16.dp)
        Spacer(Modifier.height(Spacing.sm))
        repeat(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.width(56.dp), height = 18.dp)
                SkeletonBlock(Modifier.size(20.dp), height = 20.dp)
                SkeletonBlock(Modifier.fillMaxWidth(), height = 40.dp)
            }
        }
    }
}

private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
