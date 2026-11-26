package com.example.routealarm.shared

import com.example.routealarm.shared.routeline.KoreanText
import com.example.routealarm.shared.routeline.LineColors
import com.example.routealarm.shared.routeline.RouteLegKind
import com.example.routealarm.shared.routeline.RouteLineBuilder
import com.example.routealarm.shared.routeline.RouteNodeKind
import com.example.routealarm.shared.trip.TripPlan
import com.example.routealarm.shared.trip.TripSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RouteLineTest {
    private fun at(hhmm: String) = Instant.parse("2026-11-26T${hhmm}:00Z").toEpochMilliseconds()

    private fun plan(segments: List<TripSegment>, wait: Int = 0, transfer: Int = 0) = TripPlan(
        targetArrivalMillis = at("09:00"),
        wakeUpMillis = at("07:29"),
        departureMillis = at("07:59"),
        estimatedArrivalMillis = at("08:50"),
        travelMinutes = 51,
        safetyBufferMinutes = 10,
        delayMinutes = 0,
        transferCount = 1,
        waitMinutes = wait,
        transferMinutes = transfer,
        isRealtime = true,
        usedFallback = false,
        wakeUpAlreadyPassed = false,
        isLate = false,
        lateByMinutes = 0,
        segments = segments,
        weather = null,
    )

    @Test
    fun 안드로이드_예시_경로와_같은_지점과_문구() {
        val line = RouteLineBuilder.build(
            plan(
                listOf(
                    TripSegment("WALK", null, "다산역", "한양수자인1차입구", 4, null),
                    TripSegment("BUS", "76번", "한양수자인1차입구", "화랑대역", 21, null),
                    TripSegment("SUBWAY", "6호선", "화랑대역", "창신역", 16, null),
                    TripSegment("WALK", null, "창신역", "한성대학교", 9, null),
                ),
            ),
            "다산역", "한성대학교",
        )
        assertEquals(
            listOf(RouteNodeKind.WAKE_UP, RouteNodeKind.DEPARTURE, RouteNodeKind.BOARD, RouteNodeKind.TRANSFER, RouteNodeKind.ALIGHT, RouteNodeKind.ARRIVAL),
            line.nodes.map { it.kind },
        )
        assertEquals("다산역 앞, 한양수자인1차입구까지 걷기", line.nodes[1].subtitle)
        assertEquals("76번 승차", line.nodes[2].subtitle)
        assertEquals("화랑대역 환승", line.nodes[3].title)
        assertEquals("6호선으로 갈아타기", line.nodes[3].subtitle)
        assertEquals("한성대학교 | 여유 10분", line.nodes[5].subtitle)
        assertEquals(listOf("준비 30분", "도보 4분", "76번 21분", "6호선 16분", "도보 9분"), line.legs.map { it.label })
        assertEquals(0xFFCD7C2F, line.legs[3].colorArgb)
        // 08:03 승차, 08:24 환승, 08:40 하차
        assertEquals(at("08:03"), line.nodes[2].timeMillis)
        assertEquals(at("08:24"), line.nodes[3].timeMillis)
    }

    @Test
    fun 경로가_없으면_이동_한_구간() {
        val line = RouteLineBuilder.build(plan(emptyList()), "집", "학교")
        assertEquals(3, line.nodes.size)
        assertEquals(RouteLegKind.TRANSIT, line.legs[1].kind)
        assertEquals("이동 51분", line.legs[1].label)
    }

    @Test
    fun 노선색과_조사() {
        assertEquals(0xFF00A84D, LineColors.argb("SUBWAY", "수도권 2호선"))
        assertEquals(0xFF53B332, LineColors.argb("BUS", "지선 3216"))
        assertEquals(LineColors.DEFAULT_BUS, LineColors.argb("BUS", "273"))
        assertEquals("2호선으로", KoreanText.withDirectionParticle("2호선"))
        assertEquals("6으로", KoreanText.withDirectionParticle("6"))
        assertEquals("1시간 5분", KoreanText.minutes(65))
    }
}
