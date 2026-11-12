package com.example.routealarm.presentation.schedule.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.ErrorState
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.NoticeStrip
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.Pill
import com.example.routealarm.core.designsystem.component.ProBadge
import com.example.routealarm.core.designsystem.component.RouteBar
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.theme.AppType
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.usecase.PlanOutcome
import com.example.routealarm.domain.usecase.PreviewDelayScenarioUseCase
import com.example.routealarm.presentation.common.RouteTimeline
import com.example.routealarm.presentation.common.RouteTimelineBuilder
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.messageRes
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant

/**
 * 계산 미리보기 — UI-C-006.
 * 목표 도착 시각에서 거꾸로 계산한 기상 시각과 그 근거(경로 막대, 시간 내역)를 보여준다.
 */
@Composable
internal fun PreviewStep(
    state: CreateScheduleUiState,
    isPro: Boolean,
    onRetry: () -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onShowPaywall: () -> Unit,
) {
    when (val result = state.result) {
        ResultState.Idle, ResultState.Calculating -> LoadingState(
            modifier = Modifier.padding(top = Spacing.xxl),
            message = stringResource(R.string.create_calculating),
        )
        is ResultState.Saved -> LoadingState(Modifier.padding(top = Spacing.xxl))
        is ResultState.Failed -> ErrorState(
            title = stringResource(R.string.error_title),
            message = stringResource(result.error.messageRes()),
            onRetry = onRetry,
            retryText = stringResource(R.string.common_retry),
            modifier = Modifier.padding(top = Spacing.xl),
        )
        is ResultState.Ready -> PreviewContent(state, result, isPro, onToggleDay, onShowPaywall)
    }
}

@Composable
private fun PreviewContent(
    state: CreateScheduleUiState,
    ready: ResultState.Ready,
    isPro: Boolean,
    onToggleDay: (DayOfWeek) -> Unit,
    onShowPaywall: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    val preview = ready.preview
    val plan = preview.outcome.plan
    val timeline = remember(plan, preview.resolvedOrigin.name, preview.destination.name) {
        RouteTimelineBuilder.build(plan, preview.resolvedOrigin.name, preview.destination.name)
    }
    val preparationMinutes = minutesBetween(plan.wakeUp, plan.departure)
    val travelMinutes = minutesBetween(plan.departure, plan.estimatedArrival)
    val slackMinutes = minutesBetween(plan.estimatedArrival, plan.targetArrival)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(4.dp))
        // 기상 시각(①): 화면에서 가장 크게
        Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.Bottom) {
            Text(TimeFormat.time(plan.wakeUp), style = MaterialTheme.typography.displayLarge, color = colors.textPrimary)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.create_result_wake_suffix),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        OutcomeNotices(preview.outcome, repeating = state.repeatDays.isNotEmpty())

        // 경로 막대(②): 준비·도보·노선·여유를 시간 비율대로
        Spacer(Modifier.height(18.dp))
        RouteBar(timeline, slackMinutes)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BarLabel(stringResource(R.string.create_result_bar_wake, TimeFormat.time(plan.wakeUp)))
            BarLabel(stringResource(R.string.create_result_bar_departure, TimeFormat.time(plan.departure)))
            BarLabel(stringResource(R.string.create_result_bar_arrival, TimeFormat.time(plan.estimatedArrival)))
        }

        // 시간 내역(③): 준비 + 이동 + 여유 = 합계
        Spacer(Modifier.height(18.dp))
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 합계와 바로 견줄 수 있게 시간 내역은 모두 분으로 적는다(30 + 65 + 10 = 105).
                BreakdownRow(stringResource(R.string.create_preparation_label), plainMinutes(preparationMinutes))
                BreakdownRow(travelLabel(timeline), plainMinutes(travelMinutes))
                BreakdownRow(stringResource(R.string.create_buffer_label), plainMinutes(slackMinutes))
                HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = colors.outline)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.create_result_total, TimeFormat.time(plan.targetArrival)),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        plainMinutes(preparationMinutes + travelMinutes + slackMinutes),
                        style = AppType.Figure.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                        color = colors.accentText,
                    )
                }
            }
        }

        // 반복 설정(④): 요일을 눌러 반복 일정 지정
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.detail_repeat_label), style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
            Spacer(Modifier.width(14.dp))
            DayChipRow(state.repeatDays, onToggleDay, Modifier.weight(1f))
        }
        if (state.repeatDays.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Paragraph(
                stringResource(R.string.create_result_once_hint, TimeFormat.dateWithWeekday(plan.targetArrival)),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // 지연 시나리오(⑤): 자동 조정 결과를 미리 보여 준다
        ready.delayScenarioWakeUp?.takeIf { it != plan.wakeUp }?.let { adjusted ->
            Spacer(Modifier.height(18.dp))
            DelayScenarioCard(plan, adjusted, isPro, onShowPaywall)
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** 지각·기상 시각 경과·캐시 데이터처럼 결과를 그대로 믿기 전에 알아야 할 것들 */
@Composable
private fun OutcomeNotices(outcome: PlanOutcome, repeating: Boolean) {
    val plan = outcome.plan
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (outcome) {
            is PlanOutcome.TooLate -> {
                Spacer(Modifier.height(4.dp))
                NoticeStrip(
                    title = stringResource(R.string.create_result_too_late_title, TimeFormat.time(plan.targetArrival)),
                    text = stringResource(R.string.create_result_too_late_body, TimeFormat.time(outcome.earliestArrival), outcome.lateByMinutes.toInt()) +
                        if (repeating) " " + stringResource(R.string.create_result_too_late_repeat_hint) else "",
                    icon = Icons.Outlined.ErrorOutline,
                    danger = true,
                )
            }
            is PlanOutcome.OnTime -> if (outcome.wakeUpAlreadyPassed) {
                Spacer(Modifier.height(4.dp))
                NoticeStrip(text = stringResource(R.string.create_result_wake_passed, TimeFormat.time(plan.departure)))
            }
        }
        if (!plan.isRealtime) {
            NoticeStrip(text = stringResource(R.string.create_result_cached), icon = Icons.Outlined.CloudOff)
        }
    }
}

@Composable
private fun BarLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = RouteAlarmTheme.colors.textSecondary, maxLines = 1)
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    val colors = RouteAlarmTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, style = AppType.Figure.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = colors.textPrimary)
    }
}

/** 이동 구간의 이름: "도보 + 지하철 + 환승" */
@Composable
private fun travelLabel(timeline: RouteTimeline): String {
    val parts = buildList {
        if (timeline.hasWalk) add(stringResource(R.string.transit_walk))
        timeline.transitTypes.forEach { type ->
            add(
                stringResource(
                    when (type) {
                        SegmentType.BUS -> R.string.transit_bus
                        SegmentType.SUBWAY -> R.string.transit_subway
                        SegmentType.TRAIN -> R.string.transit_train
                        else -> R.string.transit_other
                    },
                ),
            )
        }
        if (timeline.transferCount > 0) add(stringResource(R.string.transit_transfer))
    }
    return parts.distinct().joinToString(" + ").ifBlank { stringResource(R.string.transit_other) }
}

/**
 * 지연 시나리오(⑤): "만약 15분 늦어진다면?" 알람이 어떻게 옮겨지는지 미리 보여 준다.
 * 무료 플랜에서는 PRO 기능의 효과를 체험하게 하는 안내이며, 누르면 PRO 페이월이 열린다.
 */
@Composable
private fun DelayScenarioCard(plan: AlarmPlan, adjustedWakeUp: Instant, isPro: Boolean, onShowPaywall: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.create_scenario_title, PreviewDelayScenarioUseCase.DEFAULT_DELAY_MINUTES),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                ProBadge()
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val figure = AppType.Figure.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                Text(TimeFormat.time(plan.wakeUp), style = figure.copy(textDecoration = TextDecoration.LineThrough), color = colors.textTertiary)
                Text("→", style = MaterialTheme.typography.titleMedium, color = colors.textTertiary)
                Text(TimeFormat.time(adjustedWakeUp), style = figure, color = colors.accentText)
                Pill(stringResource(R.string.create_scenario_auto), containerColor = colors.accentContainer, contentColor = colors.accentText)
            }
            // 지연이 생기면 늘어난 이동시간에 지연 보정 여유까지 더해 당겨진다. 왜 그만큼인지 함께 적는다.
            val shift = minutesBetween(adjustedWakeUp, plan.wakeUp)
            val delay = PreviewDelayScenarioUseCase.DEFAULT_DELAY_MINUTES
            if (shift > delay) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.create_scenario_breakdown, delay, shift - delay),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Paragraph(
                stringResource(if (isPro) R.string.create_scenario_body_pro else R.string.create_scenario_body_free),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val border = BorderStroke(1.5.dp, colors.textPrimary)
    if (isPro) {
        Surface(Modifier.fillMaxWidth(), shape = Radius.card, color = colors.background, border = border, content = content)
    } else {
        Surface(onClick = onShowPaywall, modifier = Modifier.fillMaxWidth(), shape = Radius.card, color = colors.background, border = border, content = content)
    }
}

@Composable
private fun plainMinutes(minutes: Int): String = stringResource(R.string.common_minutes_format, minutes)

private fun minutesBetween(from: Instant, to: Instant): Int = Duration.between(from, to).toMinutes().coerceAtLeast(0).toInt()
