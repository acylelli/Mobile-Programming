package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.AppColors
import com.example.routealarm.core.designsystem.theme.AppType
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.presentation.common.RouteLeg
import com.example.routealarm.presentation.common.RouteLegKind
import com.example.routealarm.presentation.common.RouteNode
import com.example.routealarm.presentation.common.RouteNodeKind
import com.example.routealarm.presentation.common.RouteTimeline
import com.example.routealarm.presentation.common.SampleLineColors
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.minutesText
import com.example.routealarm.presentation.common.transitLineColor
import com.example.routealarm.presentation.common.transitLineTint
import com.example.routealarm.presentation.common.withDirectionParticle
import java.time.Instant
import java.time.ZoneId

private val TimeColumnWidth = 64.dp
private val RailWidth = 56.dp
private val LineWidth = 5.dp

/**
 * 기상·도착 지점 표시. 알람 아이콘이 들어간 강조색 점이라 3호선(주황) 역 표시와 구분된다(UI-B-001 ③).
 */
@Composable
fun AlarmDot(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val colors = RouteAlarmTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .background(colors.background, CircleShape)
            .border(1.5.dp, colors.accent, CircleShape)
            .padding(size * 0.11f)
            .background(colors.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Alarm, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(size * 0.42f))
    }
}

/** 역·정류장 지점: 노선색 고리 */
@Composable
fun RingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, stroke: Dp = 5.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .padding(stroke)
            .background(RouteAlarmTheme.colors.background, CircleShape),
    )
}

private data class RailStyle(val color: Color, val dashed: Boolean = false)

@Composable
private fun railStyle(leg: RouteLeg?): RailStyle? {
    val colors = RouteAlarmTheme.colors
    return when (leg?.kind) {
        null -> null
        RouteLegKind.PREPARATION -> RailStyle(colors.dotted, dashed = true)
        RouteLegKind.WALK -> RailStyle(colors.walkSegment)
        RouteLegKind.TRANSIT -> RailStyle(transitLineColor(leg.lineType, leg.lineName))
    }
}

/** 세로 선을 그리는 레일. 위 절반과 아래 절반의 색이 다를 수 있다(지점에서 구간이 바뀌므로). */
@Composable
private fun Rail(upper: RailStyle?, lower: RailStyle?, modifier: Modifier = Modifier, dot: (@Composable () -> Unit)? = null) {
    Box(modifier.width(RailWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val x = size.width / 2
            val stroke = LineWidth.toPx()
            fun segment(style: RailStyle, from: Float, to: Float) = drawLine(
                color = style.color,
                start = Offset(x, from),
                end = Offset(x, to),
                strokeWidth = stroke,
                pathEffect = if (style.dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
            )
            upper?.let { segment(it, 0f, size.height / 2) }
            lower?.let { segment(it, size.height / 2, size.height) }
        }
        dot?.invoke()
    }
}

/**
 * 가장 가까운 일정의 기상 → 출발 → 역 → 환승 → 도착 노선도(UI-B-001).
 *
 * 노선은 공식 노선색과 소요 시간으로, 준비는 점선, 도보는 회색 선으로 구분한다.
 * 지도 대신 노선도를 쓰는 이유: 사용자가 알고 싶은 것은 위치가 아니라 "왜 이 시각에 깨우는지"의 시간 흐름이다.
 */
@Composable
fun RouteTimelineView(
    timeline: RouteTimeline,
    slackMinutes: Long,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier.fillMaxWidth()) {
        timeline.nodes.forEachIndexed { index, node ->
            val before = timeline.legs.getOrNull(index - 1)
            val after = timeline.legs.getOrNull(index)
            NodeRow(node, before, after, slackMinutes, zone)
            if (after != null) LegRow(after)
        }
    }
}

@Composable
private fun NodeRow(node: RouteNode, before: RouteLeg?, after: RouteLeg?, slackMinutes: Long, zone: ZoneId) {
    val colors = RouteAlarmTheme.colors
    val isAlarmPoint = node.kind == RouteNodeKind.WAKE_UP || node.kind == RouteNodeKind.ARRIVAL
    val title = nodeTitle(node)
    val subtitle = nodeSubtitle(node, slackMinutes)

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            TimeFormat.time(node.time, zone),
            style = AppType.NodeTime,
            color = if (isAlarmPoint) colors.accentText else colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(TimeColumnWidth),
        )
        Rail(upper = railStyle(before), lower = railStyle(after)) {
            when (node.kind) {
                RouteNodeKind.WAKE_UP, RouteNodeKind.ARRIVAL -> AlarmDot()
                RouteNodeKind.DEPARTURE, RouteNodeKind.TRANSFER -> RingDot(colors.textPrimary)
                RouteNodeKind.BOARD, RouteNodeKind.ALIGHT ->
                    RingDot(if (node.lineType != null) transitLineColor(node.lineType, node.lineName) else colors.walkSegment)
            }
        }
        Column(Modifier.weight(1f).padding(vertical = if (isAlarmPoint) 0.dp else 3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LegRow(leg: RouteLeg) {
    val colors = RouteAlarmTheme.colors
    val style = railStyle(leg)
    val lineColor = transitLineColor(leg.lineType, leg.lineName)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(TimeColumnWidth))
        Rail(upper = style, lower = style)
        Box(Modifier.padding(vertical = if (leg.kind == RouteLegKind.TRANSIT) 14.dp else 8.dp)) {
            when (leg.kind) {
                RouteLegKind.TRANSIT -> Pill(legLabel(leg), containerColor = transitLineTint(lineColor), contentColor = lineColor)
                else -> Pill(legLabel(leg), containerColor = colors.surfaceStrong, contentColor = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun nodeTitle(node: RouteNode): String = when (node.kind) {
    RouteNodeKind.WAKE_UP -> stringResource(R.string.route_node_wake_up)
    RouteNodeKind.DEPARTURE -> stringResource(R.string.route_node_departure)
    RouteNodeKind.TRANSFER -> stringResource(R.string.route_node_transfer_format, node.placeName)
    RouteNodeKind.BOARD, RouteNodeKind.ALIGHT -> node.placeName
    RouteNodeKind.ARRIVAL -> stringResource(R.string.route_node_arrival)
}

@Composable
private fun nodeSubtitle(node: RouteNode, slackMinutes: Long): String? = when (node.kind) {
    RouteNodeKind.WAKE_UP -> stringResource(R.string.route_node_wake_up_body_format, node.placeName)
    RouteNodeKind.DEPARTURE -> when {
        node.walkTo != null -> stringResource(R.string.route_node_departure_walk_format, node.placeName, node.walkTo)
        node.lineName != null -> stringResource(R.string.route_node_departure_ride_format, node.placeName, node.lineName)
        else -> node.placeName
    }
    RouteNodeKind.BOARD -> stringResource(R.string.route_node_board_format, lineDisplayName(node.lineType, node.lineName))
    RouteNodeKind.TRANSFER ->
        stringResource(R.string.route_node_transfer_body_format, withDirectionParticle(lineDisplayName(node.lineType, node.lineName)))
    RouteNodeKind.ALIGHT -> null
    RouteNodeKind.ARRIVAL -> if (slackMinutes > 0) {
        stringResource(R.string.route_node_arrival_body_format, node.placeName, slackMinutes.toInt())
    } else {
        node.placeName
    }
}

/** 노선 이름이 없으면 교통수단 이름으로 대신한다. */
@Composable
fun lineDisplayName(type: SegmentType?, lineName: String?): String =
    lineName?.takeIf { it.isNotBlank() } ?: stringResource(
        when (type) {
            SegmentType.BUS -> R.string.transit_bus
            SegmentType.SUBWAY -> R.string.transit_subway
            SegmentType.TRAIN -> R.string.transit_train
            else -> R.string.transit_other
        },
    )

@Composable
private fun legLabel(leg: RouteLeg): String = when (leg.kind) {
    RouteLegKind.PREPARATION -> stringResource(R.string.route_leg_preparation_format, minutesText(leg.minutes))
    RouteLegKind.WALK -> stringResource(R.string.route_leg_walk_format, minutesText(leg.minutes))
    RouteLegKind.TRANSIT ->
        stringResource(R.string.route_leg_transit_format, lineDisplayName(leg.lineType, leg.lineName), minutesText(leg.minutes))
}

/* ───────────────────────── 가로 노선(온보딩, 기상 알람 "오늘의 경로") ───────────────────────── */

data class StripStop(val isAlarmPoint: Boolean, val color: Color)
data class StripLink(val color: Color, val weight: Float)

@Composable
fun RouteStrip(
    stops: List<StripStop>,
    links: List<StripLink>,
    modifier: Modifier = Modifier,
    alarmDotSize: Dp = 32.dp,
    ringSize: Dp = 18.dp,
) {
    // 선이 점 뒤로 살짝 들어가 끊김 없이 이어져 보이도록 겹친다.
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
        stops.forEachIndexed { index, stop ->
            if (stop.isAlarmPoint) {
                AlarmDot(Modifier.zIndex(1f), alarmDotSize)
            } else {
                RingDot(stop.color, Modifier.zIndex(1f), ringSize)
            }
            links.getOrNull(index)?.let { link ->
                Box(Modifier.weight(link.weight.coerceAtLeast(0.1f)).height(LineWidth).background(link.color))
            }
        }
    }
}

/** 출발 → (승차) → 환승 → 도착만 남긴 요약 노선. 내려서 걷는 구간은 앞 노선에 합쳐 단순하게 보여준다. */
@Composable
fun TimelineStrip(timeline: RouteTimeline, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    val (stops, links) = remember(timeline, colors) { timeline.toStrip(colors) }
    RouteStrip(stops, links, modifier)
}

private fun RouteTimeline.toStrip(colors: AppColors): Pair<List<StripStop>, List<StripLink>> {
    val stops = mutableListOf<StripStop>()
    val links = mutableListOf<StripLink>()
    val start = nodes.indexOfFirst { it.kind == RouteNodeKind.DEPARTURE }.coerceAtLeast(0)
    val totalMinutes = legs.drop(start).sumOf { it.minutes }.coerceAtLeast(1)
    val minimumWeight = totalMinutes * 0.12f

    for (index in start until nodes.size) {
        val node = nodes[index]
        val following = legs.getOrNull(index)
        if (node.kind == RouteNodeKind.ALIGHT) {
            if (following != null && links.isNotEmpty()) {
                links[links.lastIndex] = links.last().let { it.copy(weight = it.weight + following.minutes) }
            }
            continue
        }
        stops += when (node.kind) {
            RouteNodeKind.ARRIVAL, RouteNodeKind.WAKE_UP -> StripStop(isAlarmPoint = true, color = colors.accent)
            RouteNodeKind.BOARD -> StripStop(false, transitLineColor(node.lineType, node.lineName))
            else -> StripStop(false, colors.textPrimary)
        }
        if (following != null && node.kind != RouteNodeKind.ARRIVAL) {
            val color = when (following.kind) {
                RouteLegKind.PREPARATION -> colors.dotted
                RouteLegKind.WALK -> colors.walkSegment
                RouteLegKind.TRANSIT -> transitLineColor(following.lineType, following.lineName)
            }
            links += StripLink(color, following.minutes.toFloat().coerceAtLeast(minimumWeight))
        }
    }
    return stops to links
}

/** 시각과 설명을 위아래로 적는 요약 라벨(07:45 / 출발) */
@Composable
fun StripLabel(time: String, caption: String, alignment: Alignment.Horizontal, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    Column(modifier, horizontalAlignment = alignment) {
        Text(time, style = AppType.NodeTime, color = colors.textPrimary, maxLines = 1)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ───────────────────────── 경로 막대(계산 미리보기) ───────────────────────── */

/**
 * 준비·도보·노선·여유 구간을 시간 비율대로 이어 붙인 막대(UI-C-006 ②).
 * 점은 출발(회색), 환승(검정), 도착(회색) 지점이다.
 */
@Composable
fun RouteBar(timeline: RouteTimeline, slackMinutes: Int, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    val total = (timeline.legs.sumOf { it.minutes } + slackMinutes.coerceAtLeast(0)).coerceAtLeast(1)
    val minimumWeight = total * 0.04f
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        timeline.legs.forEachIndexed { index, leg ->
            val color = when (leg.kind) {
                RouteLegKind.PREPARATION -> colors.dotted
                RouteLegKind.WALK -> colors.walkSegment
                RouteLegKind.TRANSIT -> transitLineColor(leg.lineType, leg.lineName)
            }
            if (leg.minutes > 0) {
                Box(Modifier.weight(leg.minutes.toFloat().coerceAtLeast(minimumWeight)).height(10.dp).background(color, CircleShape))
            }
            when (timeline.nodes.getOrNull(index + 1)?.kind) {
                RouteNodeKind.DEPARTURE, RouteNodeKind.ARRIVAL -> BarDot(colors.walkSegment)
                RouteNodeKind.TRANSFER -> BarDot(colors.textPrimary)
                else -> Unit
            }
        }
        if (slackMinutes > 0) {
            Box(Modifier.weight(slackMinutes.toFloat().coerceAtLeast(minimumWeight)).height(10.dp).background(colors.accent, CircleShape))
        }
    }
}

@Composable
private fun BarDot(color: Color) {
    Box(Modifier.size(12.dp).background(color, CircleShape))
}

/** 온보딩과 테마 미리보기에 쓰는 고정 예시 노선: 기상 ┄ 출발 ━(8호선)━ 환승 ━(2호선)━ 도착 */
@Composable
fun SampleRouteStrip(modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    RouteStrip(
        stops = listOf(
            StripStop(true, colors.accent),
            StripStop(false, colors.textPrimary),
            StripStop(false, SampleLineColors.Line2),
            StripStop(true, colors.accent),
        ),
        links = listOf(
            StripLink(colors.dotted, 30f),
            StripLink(SampleLineColors.Line8, 31f),
            StripLink(SampleLineColors.Line2, 34f),
        ),
        modifier = modifier,
    )
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun RouteTimelinePreview() {
    val wake = Instant.parse("2026-10-22T22:15:00Z")
    fun at(minutes: Long) = wake.plusSeconds(minutes * 60)
    val timeline = RouteTimeline(
        nodes = listOf(
            RouteNode(RouteNodeKind.WAKE_UP, at(0), "집"),
            RouteNode(RouteNodeKind.DEPARTURE, at(30), "집", walkTo = "다산역"),
            RouteNode(RouteNodeKind.BOARD, at(38), "다산역", "8호선", SegmentType.SUBWAY),
            RouteNode(RouteNodeKind.TRANSFER, at(69), "잠실역", "2호선", SegmentType.SUBWAY),
            RouteNode(RouteNodeKind.ALIGHT, at(88), "한성대입구역", "2호선", SegmentType.SUBWAY),
            RouteNode(RouteNodeKind.ARRIVAL, at(95), "한성대학교"),
        ),
        legs = listOf(
            RouteLeg(RouteLegKind.PREPARATION, 30),
            RouteLeg(RouteLegKind.WALK, 8),
            RouteLeg(RouteLegKind.TRANSIT, 31, "8호선", SegmentType.SUBWAY),
            RouteLeg(RouteLegKind.TRANSIT, 18, "2호선", SegmentType.SUBWAY),
            RouteLeg(RouteLegKind.WALK, 7),
        ),
    )
    RouteAlarmTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RouteTimelineView(timeline, slackMinutes = 10, zone = ZoneId.of("Asia/Seoul"))
            RouteBar(timeline, slackMinutes = 10)
            TimelineStrip(timeline)
            SampleRouteStrip()
        }
    }
}
