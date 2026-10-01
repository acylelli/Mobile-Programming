package com.example.routealarm.domain.repository

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.RouteQueryResult
import java.time.Instant

/**
 * 대중교통 경로 조회의 유일한 진입점.
 *
 * 설계 이유: 앱 전체가 특정 교통 API(ODsay, TMAP, 공공데이터 등)에 종속되지 않도록
 * Domain 은 이 인터페이스만 안다. 제공자 교체는 data 계층의 RemoteDataSource 구현만 바꾸면 된다.
 * 구현체는 네트워크 실패 시 Room 캐시로 폴백(Offline First)할 책임을 진다.
 */
interface TransitRepository {
    suspend fun getRoutes(
        origin: Place,
        destination: Place,
        departureTime: Instant,
    ): AppResult<RouteQueryResult>
}
