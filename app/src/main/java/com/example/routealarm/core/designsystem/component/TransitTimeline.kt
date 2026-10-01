package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.domain.model.TransitSegment
import com.example.routealarm.presentation.common.minutesText
import java.time.Duration
import java.time.Instant

/**
 * 경로를 "장소 → 구간 → 장소" 타임라인으로 보여준다. 지도보다 이동 순서가 한눈에 보이는 것이 목적.
 */
@Composable
fun TransitTimeline(route: TransitRoute, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.appColors
    Column(modifier = modifier.fillMaxWidth()) {
        route.segments.forEachIndexed { index, segment ->
            if (index == 0) TimelinePlace(name = segment.startName, isTerminal = true)
            val (icon, color, label, cd) = when (segment.type) {
                SegmentType.WALK -> SegmentStyle(Icons.Outlined.DirectionsWalk, colors.walkSegment, stringResource(R.string.segment_walk, minutesText(segment.duration.toMinutes().toInt())), stringResource(R.string.cd_route_segment_walk))
                SegmentType.BUS -> SegmentStyle(Icons.Outlined.DirectionsBus, colors.busSegment, stringResource(R.string.segment_bus, segment.lineName.orEmpty(), minutesText(segment.duration.toMinutes().toInt())), stringResource(R.string.cd_route_segment_transit))
                SegmentType.SUBWAY -> SegmentStyle(Icons.Outlined.Subway, colors.subwaySegment, stringResource(R.string.segment_subway, segment.lineName.orEmpty(), minutesText(segment.duration.toMinutes().toInt())), stringResource(R.string.cd_route_segment_transit))
                SegmentType.TRAIN -> SegmentStyle(Icons.Outlined.Train, colors.trainSegment, stringResource(R.string.segment_train, segment.lineName.orEmpty(), minutesText(segment.duration.toMinutes().toInt())), stringResource(R.string.cd_route_segment_transit))
                SegmentType.ETC -> SegmentStyle(Icons.Outlined.Place, colors.textSecondary, stringResource(R.string.segment_etc, minutesText(segment.duration.toMinutes().toInt())), stringResource(R.string.cd_route_segment_transit))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(2.dp).height(36.dp).background(color.copy(alpha = 0.5f)))
                }
                Spacer(Modifier.width(12.dp))
                Icon(icon, contentDescription = cd, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    segment.arrivalInfo?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TimelinePlace(name = segment.endName, isTerminal = index == route.segments.lastIndex)
        }
    }
}

private data class SegmentStyle(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val label: String,
    val contentDescription: String,
)

@Composable
private fun TimelinePlace(name: String, isTerminal: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(if (isTerminal) 12.dp else 8.dp)
                    .background(
                        if (isTerminal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            style = if (isTerminal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (isTerminal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RouteSummaryText(route: TransitRoute, modifier: Modifier = Modifier) {
    Text(
        stringResource(
            R.string.route_summary_format,
            minutesText(route.totalDuration.toMinutes().toInt()),
            minutesText(route.walkingDuration.toMinutes().toInt()),
            route.transferCount,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun TransitTimelinePreview() {
    val now = Instant.now()
    val route = TransitRoute(
        totalDuration = Duration.ofMinutes(67),
        walkingDuration = Duration.ofMinutes(15),
        waitingDuration = Duration.ofMinutes(3),
        transferDuration = Duration.ofMinutes(3),
        departureTime = now,
        arrivalTime = now.plusSeconds(67 * 60),
        transferCount = 1,
        segments = listOf(
            TransitSegment(SegmentType.WALK, null, "집", "다산역", Duration.ofMinutes(8)),
            TransitSegment(SegmentType.SUBWAY, "8호선", "다산역", "잠실역", Duration.ofMinutes(31), "3분 후 도착"),
            TransitSegment(SegmentType.SUBWAY, "2호선", "잠실역", "한성대입구역", Duration.ofMinutes(18)),
            TransitSegment(SegmentType.WALK, null, "한성대입구역", "한성대학교", Duration.ofMinutes(7)),
        ),
    )
    RouteAlarmTheme { TransitTimeline(route, Modifier.padding(16.dp)) }
}
