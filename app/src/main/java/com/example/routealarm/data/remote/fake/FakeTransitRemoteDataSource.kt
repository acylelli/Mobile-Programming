package com.example.routealarm.data.remote.fake

import com.example.routealarm.data.remote.TransitRemoteDataSource
import com.example.routealarm.data.remote.dto.RouteDto
import com.example.routealarm.data.remote.dto.RouteResponseDto
import com.example.routealarm.data.remote.dto.SegmentDto
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.repository.PreferencesRepository
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * API Key 없이 앱 전체 흐름을 시연하기 위한 가짜 교통 API. ("FakeTransitRepository" 요구사항의 구현)
 *
 * Repository 가 아니라 RemoteDataSource 레벨에서 가짜를 두는 이유:
 * 실제 Proxy 와 똑같은 DTO 를 반환하므로 DTO→Domain 매핑, Room 캐시, 네트워크 실패 폴백 같은
 * 핵심 로직이 시연 중에도 실제와 동일하게 동작한다. 실제 API 로 바꿀 때는 DI 에서 이 클래스만 교체된다.
 *
 * - 좌표 사이 거리로 도보/탑승/환승 시간을 결정적으로(같은 입력 → 같은 결과) 계산한다.
 * - 설정 > 데모 옵션의 "교통 지연"과 "네트워크 오류"를 반영해 알람 자동 조정/오프라인 동작을 시연할 수 있다.
 */
@Singleton
class FakeTransitRemoteDataSource @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : TransitRemoteDataSource {

    override suspend fun fetchRoutes(origin: Place, destination: Place, departureTime: Instant): RouteResponseDto {
        delay(SIMULATED_LATENCY_MILLIS)
        val demo = preferencesRepository.current().demo
        if (demo.simulateNetworkFailure) throw IOException("Simulated network failure")

        val extraDelay = demo.extraDelayMinutes.coerceAtLeast(0)
        val route = if (isDemoRoute(origin, destination)) {
            demoRoute(departureTime, extraDelay)
        } else {
            generatedRoute(origin, destination, departureTime, extraDelay)
        }
        return RouteResponseDto(realtime = true, routes = listOf(route))
    }

    /** 명세 예시와 같은 "다산동 → 한성대학교" 65분 경로 */
    private fun demoRoute(departure: Instant, extraDelay: Int): RouteDto {
        val segments = listOf(
            SegmentDto("WALK", null, "집", "다산역", 8),
            SegmentDto("SUBWAY", "8호선", "다산역", "잠실역", 30 + extraDelay, "3분 후 도착"),
            SegmentDto("SUBWAY", "2호선", "잠실역", "한성대입구역", 15),
            SegmentDto("WALK", null, "한성대입구역", "한성대학교", 7),
        )
        return buildRoute(departure, segments, waitMinutes = 2, transferMinutes = 3, transferCount = 1, extraDelay)
    }

    private fun generatedRoute(origin: Place, destination: Place, departure: Instant, extraDelay: Int): RouteDto {
        val km = distanceKm(origin, destination)
        if (km < WALK_ONLY_KM) {
            val walk = (km * WALK_MINUTES_PER_KM).roundToInt().coerceAtLeast(1) + extraDelay
            return buildRoute(
                departure, listOf(SegmentDto("WALK", null, origin.name, destination.name, walk)),
                waitMinutes = 0, transferMinutes = 0, transferCount = 0, extraDelay = extraDelay,
            )
        }
        val ride = (km * RIDE_MINUTES_PER_KM).roundToInt() + RIDE_BASE_MINUTES + extraDelay
        val startStop = "${origin.name} 인근 정류장"
        val endStop = "${destination.name} 인근 정류장"
        val segments = if (km > TRANSFER_KM) {
            val first = (ride * FIRST_LEG_RATIO).roundToInt()
            listOf(
                SegmentDto("WALK", null, origin.name, startStop, ACCESS_WALK_MINUTES),
                SegmentDto("SUBWAY", "지하철", startStop, "환승역", first, "4분 후 도착"),
                SegmentDto("BUS", "간선버스", "환승역", endStop, ride - first),
                SegmentDto("WALK", null, endStop, destination.name, EGRESS_WALK_MINUTES),
            )
        } else {
            listOf(
                SegmentDto("WALK", null, origin.name, startStop, ACCESS_WALK_MINUTES),
                SegmentDto("BUS", "간선버스", startStop, endStop, ride, "6분 후 도착"),
                SegmentDto("WALK", null, endStop, destination.name, EGRESS_WALK_MINUTES),
            )
        }
        val hasTransfer = km > TRANSFER_KM
        return buildRoute(
            departure, segments,
            waitMinutes = WAIT_MINUTES,
            transferMinutes = if (hasTransfer) TRANSFER_MINUTES else 0,
            transferCount = if (hasTransfer) 1 else 0,
            extraDelay = extraDelay,
        )
    }

    private fun buildRoute(
        departure: Instant,
        segments: List<SegmentDto>,
        waitMinutes: Int,
        transferMinutes: Int,
        transferCount: Int,
        extraDelay: Int,
    ): RouteDto {
        val walk = segments.filter { it.mode == "WALK" }.sumOf { it.minutes }
        val total = segments.sumOf { it.minutes } + waitMinutes + transferMinutes
        return RouteDto(
            totalMinutes = total,
            walkMinutes = walk,
            waitMinutes = waitMinutes,
            transferMinutes = transferMinutes,
            transferCount = transferCount,
            delayMinutes = extraDelay,
            departureTime = ISO.format(departure.atZone(SEOUL)),
            arrivalTime = ISO.format(departure.plusSeconds(total * SECONDS_PER_MINUTE).atZone(SEOUL)),
            segments = segments,
        )
    }

    private fun isDemoRoute(origin: Place, destination: Place) =
        origin.isSameLocationAs(DEMO_ORIGIN) && destination.isSameLocationAs(DEMO_DESTINATION)

    private fun distanceKm(a: Place, b: Place): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
    }

    companion object {
        /** 데모 데이터와 동일한 좌표(DemoDataProviderImpl) */
        val DEMO_ORIGIN = Place(name = "", address = "", latitude = 37.6245, longitude = 127.1532)
        val DEMO_DESTINATION = Place(name = "", address = "", latitude = 37.5826, longitude = 127.0105)

        private const val SIMULATED_LATENCY_MILLIS = 450L
        private const val EARTH_RADIUS_KM = 6371.0
        private const val WALK_ONLY_KM = 1.2
        private const val WALK_MINUTES_PER_KM = 13.0
        private const val RIDE_MINUTES_PER_KM = 2.2
        private const val RIDE_BASE_MINUTES = 4
        private const val TRANSFER_KM = 10.0
        private const val FIRST_LEG_RATIO = 0.6
        private const val ACCESS_WALK_MINUTES = 6
        private const val EGRESS_WALK_MINUTES = 5
        private const val WAIT_MINUTES = 4
        private const val TRANSFER_MINUTES = 4
        private const val SECONDS_PER_MINUTE = 60L
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}
