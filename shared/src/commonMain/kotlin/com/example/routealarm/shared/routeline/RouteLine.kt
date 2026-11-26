package com.example.routealarm.shared.routeline

import com.example.routealarm.shared.trip.TripPlan

enum class RouteNodeKind { WAKE_UP, DEPARTURE, BOARD, TRANSFER, ALIGHT, ARRIVAL }

enum class RouteLegKind { PREPARATION, WALK, TRANSIT }

/** 노선도의 지점. 문구까지 만들어 두어 iOS 화면은 그리기만 한다. */
data class RouteNode(
    val kind: RouteNodeKind,
    /** 그 지점에 도착하는 시각(epoch 밀리초) */
    val timeMillis: Long,
    val title: String,
    val subtitle: String?,
    /** 역·정류장 고리 색(ARGB). 기상·도착은 강조색, 출발·환승은 검은 고리라 null */
    val colorArgb: Long?,
) {
    val isAlarmPoint: Boolean get() = kind == RouteNodeKind.WAKE_UP || kind == RouteNodeKind.ARRIVAL
}

/** 노선도의 구간. nodes[i] 와 nodes[i+1] 을 잇는다. */
data class RouteLeg(
    val kind: RouteLegKind,
    val minutes: Int,
    /** "준비 30분", "도보 4분", "76번 21분" */
    val label: String,
    /** 선 색(ARGB). 준비는 점선, 도보는 회색이라 null */
    val colorArgb: Long?,
)

data class RouteLine(val nodes: List<RouteNode>, val legs: List<RouteLeg>)

/**
 * 계산 결과를 기상 → 출발 → 역 → 환승 → 도착 노선도로 펼친다(안드로이드 홈·상세와 같은 규칙·문구).
 *
 * 각 지점에는 "그곳에 도착한 시각"을 적는다. 승차 대기와 환승 이동 시간은 다음 지점 시각에만 반영하고
 * 구간 칩에는 순수 탑승 시간을 보여준다.
 */
object RouteLineBuilder {
    private const val MINUTE = 60_000L

    fun build(plan: TripPlan, originName: String, destinationName: String): RouteLine {
        val nodes = mutableListOf<RouteNode>()
        val legs = mutableListOf<RouteLeg>()
        val segments = plan.segments.filter { it.minutes >= 0 }
        fun isTransit(mode: String) = mode != "WALK"

        nodes += RouteNode(RouteNodeKind.WAKE_UP, plan.wakeUpMillis, "기상", "${originName}에서 준비 시작", null)
        val prepMinutes = ((plan.departureMillis - plan.wakeUpMillis) / MINUTE).toInt().coerceAtLeast(0)
        legs += RouteLeg(RouteLegKind.PREPARATION, prepMinutes, "준비 ${KoreanText.minutes(prepMinutes)}", null)

        val first = segments.firstOrNull()
        val departureSubtitle = when {
            first == null -> originName
            !isTransit(first.mode) -> "$originName 앞, ${first.to}까지 걷기"
            first.lineName != null -> "${originName}에서 ${first.lineName} 승차"
            else -> originName
        }
        nodes += RouteNode(RouteNodeKind.DEPARTURE, plan.departureMillis, "출발", departureSubtitle, null)

        val slack = ((plan.targetArrivalMillis - plan.estimatedArrivalMillis) / MINUTE).toInt()
        val arrival = RouteNode(
            RouteNodeKind.ARRIVAL,
            plan.estimatedArrivalMillis,
            "도착 예정",
            if (slack > 0) "$destinationName | 여유 ${slack}분" else destinationName,
            null,
        )

        if (segments.isEmpty()) {
            // 경로를 받지 못한 계산(기본 이동시간): 이동 전체를 한 구간으로 보여준다.
            legs += RouteLeg(RouteLegKind.TRANSIT, plan.travelMinutes, "이동 ${KoreanText.minutes(plan.travelMinutes)}", LineColors.DEFAULT_OTHER)
            nodes += arrival
            return RouteLine(nodes, legs)
        }

        val transfers = segments.zipWithNext().count { (a, b) -> isTransit(a.mode) && isTransit(b.mode) }
        val transferGap = if (transfers <= 0) 0L else plan.transferMinutes * MINUTE / transfers
        var cursor = plan.departureMillis
        var boardedOnce = false

        segments.forEachIndexed { index, segment ->
            if (isTransit(segment.mode)) {
                cursor += if (boardedOnce) transferGap else plan.waitMinutes * MINUTE
                boardedOnce = true
                val name = KoreanText.lineDisplayName(segment.mode, segment.lineName)
                legs += RouteLeg(
                    RouteLegKind.TRANSIT,
                    segment.minutes,
                    "$name ${KoreanText.minutes(segment.minutes)}",
                    LineColors.argb(segment.mode, segment.lineName),
                )
            } else {
                legs += RouteLeg(RouteLegKind.WALK, segment.minutes, "도보 ${KoreanText.minutes(segment.minutes)}", null)
            }
            cursor += segment.minutes * MINUTE

            val next = segments.getOrNull(index + 1)
            nodes += when {
                next == null -> arrival
                isTransit(next.mode) && isTransit(segment.mode) -> RouteNode(
                    RouteNodeKind.TRANSFER, cursor, "${next.from} 환승",
                    "${KoreanText.withDirectionParticle(KoreanText.lineDisplayName(next.mode, next.lineName))} 갈아타기", null,
                )
                isTransit(next.mode) -> RouteNode(
                    RouteNodeKind.BOARD, cursor, next.from,
                    "${KoreanText.lineDisplayName(next.mode, next.lineName)} 승차", LineColors.argb(next.mode, next.lineName),
                )
                else -> RouteNode(
                    RouteNodeKind.ALIGHT, cursor, segment.to, null,
                    if (isTransit(segment.mode)) LineColors.argb(segment.mode, segment.lineName) else null,
                )
            }
        }
        return RouteLine(nodes, legs)
    }
}
