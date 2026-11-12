package com.example.routealarm.presentation.common

import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.domain.model.TransitSegment
import com.example.routealarm.fake.TestData.seoul
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

/**
 * 홈 노선도(UI-B-001)에 표시되는 지점·구간이 설계서 예시와 같은지 검증한다.
 */
class RouteTimelineBuilderTest {

    private val day = LocalDate.of(2026, 10, 23)

    private fun segment(type: SegmentType, line: String?, from: String, to: String, minutes: Long) =
        TransitSegment(type, line, from, to, Duration.ofMinutes(minutes))

    private fun plan(
        segments: List<TransitSegment>,
        waitMinutes: Long = 0,
        transferMinutes: Long = 0,
        travelMinutes: Int = 65,
    ): AlarmPlan {
        val departure = seoul(day, 7, 45)
        val route = TransitRoute(
            totalDuration = Duration.ofMinutes(travelMinutes.toLong()),
            walkingDuration = Duration.ofMinutes(segments.filter { it.type == SegmentType.WALK }.sumOf { it.duration.toMinutes() }),
            waitingDuration = Duration.ofMinutes(waitMinutes),
            transferDuration = Duration.ofMinutes(transferMinutes),
            departureTime = departure,
            arrivalTime = departure.plus(Duration.ofMinutes(travelMinutes.toLong())),
            transferCount = 1,
            segments = segments,
        )
        return AlarmPlan(
            targetArrival = seoul(day, 9, 0),
            estimatedArrival = departure.plus(Duration.ofMinutes(travelMinutes.toLong())),
            departure = departure,
            wakeUp = seoul(day, 7, 15),
            travelMinutes = travelMinutes,
            safetyBufferMinutes = 10,
            delayMinutes = 0,
            isRealtime = true,
            lastUpdatedAt = seoul(day.minusDays(1), 22, 0),
            route = route,
        )
    }

    private val hansungRoute = listOf(
        segment(SegmentType.WALK, null, "집", "다산역", 8),
        segment(SegmentType.SUBWAY, "8호선", "다산역", "잠실역", 31),
        segment(SegmentType.SUBWAY, "2호선", "잠실역", "한성대입구역", 18),
        segment(SegmentType.WALK, null, "한성대입구역", "한성대학교", 7),
    )

    @Test
    fun `설계서 예시 - 집에서 한성대학교까지 지점과 시각`() {
        val timeline = RouteTimelineBuilder.build(plan(hansungRoute, transferMinutes = 1), "집", "한성대학교")

        assertEquals(
            listOf(
                RouteNodeKind.WAKE_UP to seoul(day, 7, 15),
                RouteNodeKind.DEPARTURE to seoul(day, 7, 45),
                RouteNodeKind.BOARD to seoul(day, 7, 53),
                RouteNodeKind.TRANSFER to seoul(day, 8, 24),
                RouteNodeKind.ALIGHT to seoul(day, 8, 43),
                RouteNodeKind.ARRIVAL to seoul(day, 8, 50),
            ),
            timeline.nodes.map { it.kind to it.time },
        )
        assertEquals(listOf("집", "집", "다산역", "잠실역", "한성대입구역", "한성대학교"), timeline.nodes.map { it.placeName })
        assertEquals("다산역", timeline.nodes[1].walkTo)
        assertEquals("8호선", timeline.nodes[2].lineName)
        assertEquals("2호선", timeline.nodes[3].lineName)
        assertEquals("2호선", timeline.nodes[4].lineName)
    }

    @Test
    fun `설계서 예시 - 준비 30분 도보 8분 8호선 31분 2호선 18분 도보 7분`() {
        val timeline = RouteTimelineBuilder.build(plan(hansungRoute, transferMinutes = 1), "집", "한성대학교")

        assertEquals(
            listOf(
                RouteLegKind.PREPARATION to 30,
                RouteLegKind.WALK to 8,
                RouteLegKind.TRANSIT to 31,
                RouteLegKind.TRANSIT to 18,
                RouteLegKind.WALK to 7,
            ),
            timeline.legs.map { it.kind to it.minutes },
        )
        assertEquals(30, timeline.preparationMinutes)
        assertEquals(1, timeline.transferCount)
        assertEquals(listOf(SegmentType.SUBWAY), timeline.transitTypes)
    }

    @Test
    fun `승차 대기 시간은 다음 지점 시각에만 반영하고 구간 칩은 탑승 시간만 보여준다`() {
        val segments = listOf(
            segment(SegmentType.WALK, null, "집", "정류장", 5),
            segment(SegmentType.BUS, "간선버스", "정류장", "회사 앞", 20),
        )
        val timeline = RouteTimelineBuilder.build(plan(segments, waitMinutes = 4, travelMinutes = 29), "집", "회사")

        assertEquals(seoul(day, 7, 50), timeline.nodes[2].time) // 정류장 도착
        assertEquals(20, timeline.legs[2].minutes)
        assertEquals(seoul(day, 8, 14), timeline.nodes.last().time) // 대기 4분 + 탑승 20분
    }

    @Test
    fun `걷지 않고 바로 타는 경로는 출발 지점에 노선을 표시한다`() {
        val segments = listOf(segment(SegmentType.BUS, "간선버스", "집 앞", "학교 앞", 25))
        val timeline = RouteTimelineBuilder.build(plan(segments, travelMinutes = 25), "집", "학교")

        val departure = timeline.nodes[1]
        assertEquals(RouteNodeKind.DEPARTURE, departure.kind)
        assertEquals("간선버스", departure.lineName)
        assertNull(departure.walkTo)
        assertEquals(listOf(RouteNodeKind.WAKE_UP, RouteNodeKind.DEPARTURE, RouteNodeKind.ARRIVAL), timeline.nodes.map { it.kind })
    }

    @Test
    fun `경로 정보가 없는 계획은 이동 전체를 한 구간으로 보여준다`() {
        val offline = plan(hansungRoute).copy(route = null)
        val timeline = RouteTimelineBuilder.build(offline, "집", "한성대학교")

        assertEquals(listOf(RouteNodeKind.WAKE_UP, RouteNodeKind.DEPARTURE, RouteNodeKind.ARRIVAL), timeline.nodes.map { it.kind })
        assertEquals(listOf(30, 65), timeline.legs.map { it.minutes })
        assertEquals(seoul(day, 8, 50), timeline.nodes.last().time)
    }

    @Test
    fun `방향 조사는 받침에 따라 로 또는 으로를 붙인다`() {
        assertEquals("2호선으로", withDirectionParticle("2호선"))
        assertEquals("신분당선으로", withDirectionParticle("신분당선"))
        assertEquals("버스로", withDirectionParticle("버스"))
        assertEquals("지하철로", withDirectionParticle("지하철")) // ㄹ 받침은 "로"
        assertEquals("잠실", stationShortName("잠실역"))
    }
}
