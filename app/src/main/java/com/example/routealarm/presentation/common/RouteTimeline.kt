package com.example.routealarm.presentation.common

import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.TransitRoute
import java.time.Duration
import java.time.Instant

enum class RouteNodeKind {
    /** 기상 알람이 울리는 지점 */
    WAKE_UP,

    /** 집을 나서는 지점 */
    DEPARTURE,

    /** 걸어가서 노선에 타는 역·정류장 */
    BOARD,

    /** 노선에서 다른 노선으로 갈아타는 역 */
    TRANSFER,

    /** 노선에서 내려 걷기 시작하는 역·정류장 */
    ALIGHT,

    /** 목적지 도착 */
    ARRIVAL,
}

enum class RouteLegKind { PREPARATION, WALK, TRANSIT }

data class RouteNode(
    val kind: RouteNodeKind,
    val time: Instant,
    /** 그 지점의 이름(출발지, 역, 목적지) */
    val placeName: String,
    /** BOARD·TRANSFER: 타게 될 노선, ALIGHT: 내린 노선, DEPARTURE: 바로 타는 노선(걷지 않는 경우) */
    val lineName: String? = null,
    val lineType: SegmentType? = null,
    /** DEPARTURE 에서 걸어서 향하는 첫 지점 */
    val walkTo: String? = null,
)

data class RouteLeg(
    val kind: RouteLegKind,
    val minutes: Int,
    val lineName: String? = null,
    val lineType: SegmentType? = null,
)

/**
 * 홈의 노선도(기상 → 출발 → 역 → 환승 → 도착)를 그리기 위한 화면 모델.
 * [legs] 의 i 번째 구간이 [nodes] 의 i 번째와 i+1 번째 지점을 잇는다.
 */
data class RouteTimeline(val nodes: List<RouteNode>, val legs: List<RouteLeg>) {
    init {
        require(legs.size == (nodes.size - 1).coerceAtLeast(0)) { "legs must connect consecutive nodes" }
    }

    val preparationMinutes: Int get() = legs.firstOrNull { it.kind == RouteLegKind.PREPARATION }?.minutes ?: 0
    val hasWalk: Boolean get() = legs.any { it.kind == RouteLegKind.WALK }
    val transitTypes: List<SegmentType>
        get() = legs.filter { it.kind == RouteLegKind.TRANSIT }.mapNotNull { it.lineType }.distinct()
    val transferCount: Int get() = nodes.count { it.kind == RouteNodeKind.TRANSFER }
}

/**
 * 계산된 알람 계획을 지점·구간 목록으로 펼친다. Android 의존성이 없어 JVM 에서 테스트한다.
 *
 * 지점 시각 규칙: 각 지점에는 "그곳에 도착한 시각"을 적는다. 승차 대기와 환승 이동 시간은
 * 다음 지점의 시각에만 반영하고 구간 칩에는 순수 탑승 시간을 보여준다
 * (예: 08:24 잠실역 환승 → 2호선 18분 → 08:43 한성대입구역, 환승 1분 포함).
 */
object RouteTimelineBuilder {

    fun build(plan: AlarmPlan, originName: String, destinationName: String): RouteTimeline {
        val nodes = mutableListOf<RouteNode>()
        val legs = mutableListOf<RouteLeg>()
        val segments = plan.route?.segments.orEmpty().filter { !it.duration.isNegative }

        nodes += RouteNode(RouteNodeKind.WAKE_UP, plan.wakeUp, originName)
        legs += RouteLeg(RouteLegKind.PREPARATION, minutesBetween(plan.wakeUp, plan.departure))

        val first = segments.firstOrNull()
        nodes += RouteNode(
            kind = RouteNodeKind.DEPARTURE,
            time = plan.departure,
            placeName = originName,
            lineName = first?.takeIf { it.type != SegmentType.WALK }?.lineName,
            lineType = first?.takeIf { it.type != SegmentType.WALK }?.type,
            walkTo = first?.takeIf { it.type == SegmentType.WALK }?.endName,
        )

        if (segments.isEmpty()) {
            // 경로 스냅샷이 없는 계획(오프라인 복구 등): 이동 전체를 한 구간으로 보여준다.
            legs += RouteLeg(RouteLegKind.TRANSIT, plan.travelMinutes, lineType = SegmentType.ETC)
            nodes += RouteNode(RouteNodeKind.ARRIVAL, plan.estimatedArrival, destinationName)
            return RouteTimeline(nodes, legs)
        }

        val route = plan.route!!
        val transferGap = transferGap(route, segments.zipWithNext().count { (a, b) -> a.isTransit && b.isTransit })
        var cursor = plan.departure
        var boardedOnce = false

        segments.forEachIndexed { index, segment ->
            if (segment.isTransit) {
                // 첫 승차 전에는 대기, 이후 승차 전에는 환승 이동 시간이 든다.
                cursor = cursor.plus(if (boardedOnce) transferGap else route.waitingDuration)
                boardedOnce = true
                legs += RouteLeg(RouteLegKind.TRANSIT, segment.duration.toMinutes().toInt(), segment.lineName, segment.type)
            } else {
                legs += RouteLeg(RouteLegKind.WALK, segment.duration.toMinutes().toInt())
            }
            cursor = cursor.plus(segment.duration)

            val next = segments.getOrNull(index + 1)
            nodes += when {
                next == null -> RouteNode(RouteNodeKind.ARRIVAL, plan.estimatedArrival, destinationName)
                next.isTransit && segment.isTransit ->
                    RouteNode(RouteNodeKind.TRANSFER, cursor, next.startName, next.lineName, next.type)
                next.isTransit ->
                    RouteNode(RouteNodeKind.BOARD, cursor, next.startName, next.lineName, next.type)
                else ->
                    RouteNode(RouteNodeKind.ALIGHT, cursor, segment.endName, segment.lineName, segment.type.takeIf { segment.isTransit })
            }
        }
        return RouteTimeline(nodes, legs)
    }

    private val com.example.routealarm.domain.model.TransitSegment.isTransit: Boolean
        get() = type != SegmentType.WALK

    private fun transferGap(route: TransitRoute, transfers: Int): Duration =
        if (transfers <= 0) Duration.ZERO else route.transferDuration.dividedBy(transfers.toLong())

    private fun minutesBetween(from: Instant, to: Instant): Int =
        Duration.between(from, to).toMinutes().coerceAtLeast(0).toInt()
}
