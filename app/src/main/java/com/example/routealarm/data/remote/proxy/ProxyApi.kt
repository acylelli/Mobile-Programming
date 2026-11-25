package com.example.routealarm.data.remote.proxy

import com.example.routealarm.data.remote.GroupPassNotFoundException
import com.example.routealarm.data.remote.PlaceSearchRemoteDataSource
import com.example.routealarm.data.remote.SubscriptionRemoteDataSource
import com.example.routealarm.data.remote.TransitRemoteDataSource
import com.example.routealarm.data.remote.dto.CreateOrderRequestDto
import com.example.routealarm.data.remote.dto.CreateOrderResponseDto
import com.example.routealarm.data.remote.dto.GroupPassDto
import com.example.routealarm.data.remote.dto.PaymentResultDto
import com.example.routealarm.data.remote.dto.PlaceSearchResponseDto
import com.example.routealarm.data.remote.dto.RouteResponseDto
import com.example.routealarm.data.remote.dto.TaxiEstimateDto
import com.example.routealarm.domain.model.Place
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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

    @GET("v1/taxi/estimate")
    suspend fun estimateTaxi(
        @Query("origin_lat") originLatitude: Double,
        @Query("origin_lng") originLongitude: Double,
        @Query("destination_lat") destinationLatitude: Double,
        @Query("destination_lng") destinationLongitude: Double,
    ): TaxiEstimateDto

    @GET("v1/places/search")
    suspend fun searchPlaces(@Query("query") query: String): PlaceSearchResponseDto

    @POST("v1/payments/orders")
    suspend fun createOrder(@Body request: CreateOrderRequestDto): CreateOrderResponseDto

    @GET("v1/payments/{orderId}")
    suspend fun getPayment(@Path("orderId") orderId: String): PaymentResultDto

    @GET("v1/group-passes/{code}")
    suspend fun getGroupPass(@Path("code") code: String): GroupPassDto

    @POST("v1/group-passes/{code}/redeem")
    suspend fun redeemGroupPass(@Path("code") code: String): GroupPassDto
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

class ProxySubscriptionRemoteDataSource @Inject constructor(
    private val api: RouteAlarmProxyApi,
) : SubscriptionRemoteDataSource {
    override suspend fun verifyGroupPass(code: String): GroupPassDto = notFoundAsException(code) { api.getGroupPass(code) }

    override suspend fun redeemGroupPass(code: String): GroupPassDto = notFoundAsException(code) { api.redeemGroupPass(code) }

    /** 서버의 404(등록되지 않은 코드)는 "잘못된 코드" 안내로 이어지도록 전용 예외로 바꾼다. */
    private inline fun notFoundAsException(code: String, call: () -> GroupPassDto): GroupPassDto = try {
        call()
    } catch (e: HttpException) {
        if (e.code() == HTTP_NOT_FOUND) throw GroupPassNotFoundException(code) else throw e
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
