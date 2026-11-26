package com.example.routealarm.shared

import com.example.routealarm.shared.api.ProxyClient
import com.example.routealarm.shared.trip.PlaceSearch
import com.example.routealarm.shared.trip.Schedules
import com.example.routealarm.shared.trip.TripPlace
import com.example.routealarm.shared.trip.TripPlanner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TripPlannerTest {
    private val home = TripPlace("집", "", 37.5, 127.0)
    private val school = TripPlace("학교", "", 37.6, 127.1)
    private val target = Instant.parse("2026-10-06T00:00:00Z").toEpochMilliseconds() // 09:00 KST
    private val now = Instant.parse("2026-10-05T20:00:00Z").toEpochMilliseconds()

    private val routeJson = """
        {"realtime":true,"routes":[
          {"total_minutes":70,"transfer_count":1,"delay_minutes":0,"segments":[]},
          {"total_minutes":60,"transfer_count":0,"delay_minutes":0,"unknown":1,
           "segments":[{"mode":"WALK","from":"집","to":"정류장","minutes":5},
                       {"mode":"BUS","line_name":"273","from":"정류장","to":"학교","minutes":55,"arrival_message":"3분 후 도착"}]}
        ]}
    """.trimIndent()

    private fun client(weather: String = """{"precipitation":"NONE","probability":10}""", fail: Boolean = false) =
        ProxyClient(
            "https://example.test/",
            HttpClient(
                MockEngine { request ->
                    val json = headersOf(HttpHeaders.ContentType, "application/json")
                    when {
                        fail -> respondError(HttpStatusCode.ServiceUnavailable)
                        request.url.encodedPath.endsWith("transit/routes") -> respond(routeJson, headers = json)
                        request.url.encodedPath.endsWith("weather") -> respond(weather, headers = json)
                        request.url.encodedPath.endsWith("places/search") ->
                            respond("""{"places":[{"name":"서울역","address":"서울 용산구","latitude":37.55,"longitude":126.97}]}""", headers = json)
                        else -> respondError(HttpStatusCode.NotFound)
                    }
                },
            ),
        )

    @Test
    fun 가장_빠른_경로로_계산하고_구간을_돌려준다() = runTest {
        val plan = assertNotNull(TripPlanner(client()).plan(home, school, target, 30, 10, true, now).plan)
        // 09:00 − 60분 − 여유 10분 = 07:50 출발, 07:20 기상
        assertEquals(Instant.parse("2026-10-05T22:50:00Z").toEpochMilliseconds(), plan.departureMillis)
        assertEquals(Instant.parse("2026-10-05T22:20:00Z").toEpochMilliseconds(), plan.wakeUpMillis)
        assertEquals(2, plan.segments.size)
        assertEquals("273", plan.segments[1].lineName)
        assertTrue(plan.isRealtime)
        assertFalse(plan.usedFallback)
    }

    @Test
    fun 만약_15분_늦어지면_이동_15분과_지연_여유_5분만큼_앞당긴다() = runTest {
        val plan = assertNotNull(TripPlanner(client()).plan(home, school, target, 30, 10, false, now).plan)
        val delayed = assertNotNull(com.example.routealarm.shared.trip.DelayScenario.wakeUpMillis(plan, 30, 10))
        assertEquals(20, (plan.wakeUpMillis - delayed) / 60_000)
    }

    @Test
    fun 비_예보면_5분_일찍() = runTest {
        val plan = assertNotNull(
            TripPlanner(client("""{"precipitation":"RAIN","probability":80}""")).plan(home, school, target, 30, 10, true, now).plan,
        )
        assertEquals(Instant.parse("2026-10-05T22:15:00Z").toEpochMilliseconds(), plan.wakeUpMillis)
        assertTrue(plan.weather!!.umbrella)
    }

    @Test
    fun 서버에_닿지_못하면_기본_60분과_불확실성_여유로_계산() = runTest {
        val plan = assertNotNull(TripPlanner(client(fail = true)).plan(home, school, target, 30, 10, true, now).plan)
        assertTrue(plan.usedFallback)
        assertFalse(plan.isRealtime)
        assertNull(plan.weather)
        // 60분 + 여유 10 + 불확실성 5 → 07:45 출발, 07:15 기상
        assertEquals(Instant.parse("2026-10-05T22:15:00Z").toEpochMilliseconds(), plan.wakeUpMillis)
    }

    @Test
    fun 지난_도착_시각은_오류_문장() = runTest {
        val result = TripPlanner(client()).plan(home, school, now - 1, 30, 10, false, now)
        assertNull(result.plan)
        assertEquals("도착 시간이 이미 지났어요.", result.error)
    }

    @Test
    fun 장소_검색() = runTest {
        val result = PlaceSearch(client()).search(" 서울역 ")
        assertEquals("서울역", result.places.single().name)
        assertEquals("검색하지 못했어요. 인터넷 연결을 확인해 주세요.", PlaceSearch(client(fail = true)).search("서울역").error)
    }

    @Test
    fun 요일_비트마스크_표시() {
        assertEquals("평일", Schedules.repeatLabel(Schedules.WEEKDAYS))
        assertEquals("매일", Schedules.repeatLabel(Schedules.EVERYDAY))
        assertEquals("월, 수, 금", Schedules.repeatLabel(0b10101))
        assertEquals("평일 반복", Schedules.repeatHeader(Schedules.WEEKDAYS, null))
    }

    @Test
    fun 곧_건너뛸_공휴일() {
        // 2026-12-20(일) 기준 평일 알람은 12/25(금) 성탄절을 건너뛴다(시간대는 기기 설정)
        val now = Instant.parse("2026-12-20T03:00:00Z").toEpochMilliseconds()
        assertEquals("12월 25일 (금) 성탄절", Schedules.nextSkippedHoliday(9, 0, Schedules.WEEKDAYS, true, now))
        assertEquals(null, Schedules.nextSkippedHoliday(9, 0, Schedules.WEEKDAYS, false, now))
        assertEquals("한 번", Schedules.repeatLabel(0))
    }
}
