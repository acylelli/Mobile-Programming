package com.example.routealarm.shared.routeline

import com.example.routealarm.shared.trip.TripSegment

/** 지도에 그릴 선 하나. 탑승 구간은 노선색, 도보·환승은 회색 가는 선. 좌표는 [위도, 경도, …] */
data class MapLine(val points: List<Double>, val isWalk: Boolean, val colorArgb: Long)

/**
 * 경로를 지도 선으로 바꾼다(안드로이드 RouteMapLines 와 같은 규칙).
 * 서버는 탑승 구간의 정류장·역 좌표만 주므로, 도보 구간은 출발지·탑승역·환승역·도착지를 직선으로 잇는다.
 * 좌표가 있는 탑승 구간이 없으면 빈 목록(지도를 그리지 않는다).
 */
object RouteMapLines {
    fun build(
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double,
        segments: List<TripSegment>,
    ): List<MapLine> {
        val rides = segments.filter { it.mode != "WALK" && it.points.size >= 4 }
        if (rides.isEmpty()) return emptyList()
        val lines = mutableListOf<MapLine>()
        var last = listOf(originLatitude, originLongitude)
        rides.forEach { ride ->
            val first = ride.points.take(2)
            if (first != last) lines += walk(last, first)
            lines += MapLine(ride.points, isWalk = false, colorArgb = LineColors.argb(ride.mode, ride.lineName))
            last = ride.points.takeLast(2)
        }
        val destination = listOf(destinationLatitude, destinationLongitude)
        if (last != destination) lines += walk(last, destination)
        return lines
    }

    private fun walk(from: List<Double>, to: List<Double>) = MapLine(from + to, isWalk = true, colorArgb = WALK_ARGB)

    private const val WALK_ARGB = 0xFF9A9A9A
}
