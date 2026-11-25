package com.example.routealarm.presentation.common

import com.example.routealarm.domain.model.GeoPoint
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.TransitSegment

/** 지도에 그릴 선 하나. 탑승 구간은 노선색, 도보·환승은 회색 가는 선으로 그린다. */
data class MapLine(
    val points: List<GeoPoint>,
    val type: SegmentType,
    val lineName: String? = null,
) {
    val isWalk: Boolean get() = type == SegmentType.WALK
}

/**
 * 경로를 지도 선으로 바꾼다(Android 의존성 없음, 단위 테스트).
 * 서버는 탑승 구간의 정류장·역 좌표만 주므로, 도보 구간은 출발지·탑승역·환승역·도착지를 직선으로 잇는다.
 */
object RouteMapLines {

    fun build(origin: GeoPoint, destination: GeoPoint, segments: List<TransitSegment>): List<MapLine> {
        val rides = segments.filter { it.type != SegmentType.WALK && it.points.size >= 2 }
        if (rides.isEmpty()) return emptyList()
        val lines = mutableListOf<MapLine>()
        var last = origin
        rides.forEach { ride ->
            val first = ride.points.first()
            if (first != last) lines += MapLine(listOf(last, first), SegmentType.WALK)
            lines += MapLine(ride.points, ride.type, ride.lineName)
            last = ride.points.last()
        }
        if (last != destination) lines += MapLine(listOf(last, destination), SegmentType.WALK)
        return lines
    }

    /** 지도 화면에 모두 들어오도록 맞출 점들 */
    fun boundsPoints(lines: List<MapLine>): List<GeoPoint> = lines.flatMap { it.points }.distinct()
}
