package com.example.routealarm.data.remote

import com.example.routealarm.data.remote.dto.PlaceSearchResponseDto
import com.example.routealarm.data.remote.dto.RouteResponseDto
import com.example.routealarm.domain.model.Place
import java.time.Instant

/**
 * 교통 데이터 제공자 교체 지점.
 *
 * - [com.example.routealarm.data.remote.fake.FakeTransitRemoteDataSource]: API 키 없이 시연/개발용
 * - [com.example.routealarm.data.remote.proxy.ProxyTransitRemoteDataSource]: Backend Proxy 호출
 *
 * 둘 다 같은 DTO 를 반환하므로 캐시/오프라인/매핑 로직(DefaultTransitRepository)은 제공자와 무관하게 동일하게 검증된다.
 * 네트워크/서버 오류는 예외로 던지고, Repository 에서 AppError 로 변환한다.
 */
interface TransitRemoteDataSource {
    suspend fun fetchRoutes(origin: Place, destination: Place, departureTime: Instant): RouteResponseDto
}

interface PlaceSearchRemoteDataSource {
    suspend fun search(query: String): PlaceSearchResponseDto
}
