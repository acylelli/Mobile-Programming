package com.example.routealarm.presentation.common

import com.example.routealarm.domain.model.GeoPoint
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.TransitSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class RouteMapLinesTest {

    private val home = GeoPoint(37.6243, 127.1544)
    private val school = GeoPoint(37.5826, 127.0105)
    private val busStops = listOf(GeoPoint(37.6237, 127.1559), GeoPoint(37.6202, 127.0836))
    private val subwayStations = listOf(GeoPoint(37.6199, 127.0840), GeoPoint(37.5794, 127.0153))

    private fun segment(type: SegmentType, points: List<GeoPoint> = emptyList(), line: String? = null) =
        TransitSegment(type, line, "", "", Duration.ofMinutes(5), points = points)

    @Test
    fun `도보는 출발지·환승역·도착지를 잇고, 탑승 구간은 정류장 좌표대로 그린다`() {
        val lines = RouteMapLines.build(
            home,
            school,
            listOf(
                segment(SegmentType.WALK),
                segment(SegmentType.BUS, busStops, "202번"),
                segment(SegmentType.SUBWAY, subwayStations, "6호선"),
                segment(SegmentType.WALK),
            ),
        )

        assertEquals(
            listOf(
                MapLine(listOf(home, busStops[0]), SegmentType.WALK),
                MapLine(busStops, SegmentType.BUS, "202번"),
                MapLine(listOf(busStops[1], subwayStations[0]), SegmentType.WALK),
                MapLine(subwayStations, SegmentType.SUBWAY, "6호선"),
                MapLine(listOf(subwayStations[1], school), SegmentType.WALK),
            ),
            lines,
        )
        assertEquals(6, RouteMapLines.boundsPoints(lines).size)
    }

    @Test
    fun `좌표가 없는 예전 경로는 지도를 그리지 않는다`() {
        assertTrue(RouteMapLines.build(home, school, listOf(segment(SegmentType.SUBWAY, line = "8호선"))).isEmpty())
    }
}
