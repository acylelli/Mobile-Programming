package com.example.routealarm.domain.model

import java.time.Duration
import java.time.Instant

enum class SegmentType { WALK, BUS, SUBWAY, TRAIN, ETC }

data class TransitSegment(
    val type: SegmentType,
    val lineName: String?,
    val startName: String,
    val endName: String,
    val duration: Duration,
    /** "3분 후 도착" 같은 실시간 도착 정보. 제공자가 주지 않으면 null */
    val arrivalInfo: String? = null,
)

/**
 * 특정 교통 API 의 응답 형태와 무관한 Domain 경로 모델.
 * ODsay/TMAP/공공데이터 어떤 제공자를 쓰더라도 data/mapper 에서 이 형태로 변환된다.
 *
 * travelTime = 도보 + 탑승 + 환승 + 대기 (= [totalDuration])
 */
data class TransitRoute(
    val totalDuration: Duration,
    val walkingDuration: Duration,
    val waitingDuration: Duration,
    val transferDuration: Duration,
    val departureTime: Instant,
    val arrivalTime: Instant,
    val transferCount: Int,
    val segments: List<TransitSegment>,
    /** 평소 대비 실시간 지연. totalDuration 에 이미 포함되어 있다. */
    val delay: Duration = Duration.ZERO,
) {
    val rideDuration: Duration
        get() = totalDuration.minus(walkingDuration).minus(waitingDuration).minus(transferDuration)
            .coerceAtLeastZero()
}

/** 경로 조회 결과. 네트워크 실패 시 캐시에서 가져왔다면 isRealtime = false */
data class RouteQueryResult(
    val routes: List<TransitRoute>,
    val isRealtime: Boolean,
    val fetchedAt: Instant,
) {
    /** 가장 빨리 도착하는 경로를 기본 경로로 사용한다. */
    val bestRoute: TransitRoute? get() = routes.minByOrNull { it.totalDuration }
}

internal fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this
