package com.example.routealarm.data.remote.proxy

import com.example.routealarm.data.remote.PlaceSearchRemoteDataSource
import com.example.routealarm.data.remote.TransitRemoteDataSource
import com.example.routealarm.data.remote.dto.PlaceSearchResponseDto
import com.example.routealarm.data.remote.dto.RouteResponseDto
import com.example.routealarm.domain.model.Place
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Instant
import javax.inject.Inject

/**
 * RouteAlarm Backend Proxy API. 실제 교통/장소 API 의 Secret Key 는 Proxy 서버에만 있다.
 */
interface RouteAlarmProxyApi {
    @GET("v1/transit/routes")
    suspend fun getRoutes(
        @Query("origin_lat") originLatitude: Double,
        @Query("origin_lng") originLongitude: Double,
        @Query("destination_lat") destinationLatitude: Double,
        @Query("destination_lng") destinationLongitude: Double,
        @Query("depart_at") departAt: String,
    ): RouteResponseDto

    @GET("v1/places/search")
    suspend fun searchPlaces(@Query("query") query: String): PlaceSearchResponseDto
}

class ProxyTransitRemoteDataSource @Inject constructor(
    private val api: RouteAlarmProxyApi,
) : TransitRemoteDataSource {
    override suspend fun fetchRoutes(origin: Place, destination: Place, departureTime: Instant): RouteResponseDto =
        api.getRoutes(
            originLatitude = origin.latitude,
            originLongitude = origin.longitude,
            destinationLatitude = destination.latitude,
            destinationLongitude = destination.longitude,
            departAt = departureTime.toString(),
        )
}

class ProxyPlaceSearchRemoteDataSource @Inject constructor(
    private val api: RouteAlarmProxyApi,
) : PlaceSearchRemoteDataSource {
    override suspend fun search(query: String): PlaceSearchResponseDto = api.searchPlaces(query)
}
