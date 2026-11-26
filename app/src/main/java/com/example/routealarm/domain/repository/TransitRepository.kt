package com.example.routealarm.domain.repository

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.RouteQueryResult
import com.example.routealarm.domain.model.TaxiEstimate
import com.example.routealarm.domain.model.WeatherForecast
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

/** 지각 위험일 때 보여 줄 택시 예상 시간·요금 */
/** 출발 시각 날씨(기상청 단기예보, Proxy 경유). 약 3일 뒤까지만 있다. */
interface WeatherRepository {
    suspend fun forecast(latitude: Double, longitude: Double, at: Instant): AppResult<WeatherForecast>
}

interface TaxiRepository {
    suspend fun estimate(origin: Place, destination: Place): AppResult<TaxiEstimate>
}
