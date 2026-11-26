package com.example.routealarm.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/** 서버가 실패를 돌려줬을 때. status 0 은 연결 자체가 안 된 경우 */
class ProxyException(val status: Int, message: String) : Exception(message)

/**
 * RouteAlarm Proxy 서버(Render) 클라이언트. 안드로이드는 Retrofit 을 계속 쓰고, iOS 앱이 이 클라이언트를 쓴다.
 * client 를 주지 않으면 플랫폼 엔진(iOS: Darwin/URLSession)을 쓴다. 테스트는 MockEngine 을 넘긴다.
 * 서버는 키를 숨기고 ODsay·카카오·기상청 응답을 앱 형식으로 바꿔 준다(앱에는 API 키가 없다).
 */
class ProxyClient(
    baseUrl: String = DEFAULT_BASE_URL,
    client: HttpClient? = null,
) {
    private val http: HttpClient = (client ?: HttpClient()).config {
        install(ContentNegotiation) { json(JSON) }
        install(HttpTimeout) {
            // Render 무료 서버는 잠들어 있다가 깨는 데 30초 넘게 걸리기도 한다.
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
        }
        defaultRequest { url(baseUrl.trimEnd('/') + "/") }
        expectSuccess = false
    }

    suspend fun searchPlaces(query: String): List<PlaceDto> =
        call { http.get("v1/places/search") { parameter("query", query) } }.body<PlaceSearchResponseDto>().places

    suspend fun routes(
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double,
        departAt: Instant,
    ): RouteResponseDto = call {
        http.get("v1/transit/routes") {
            parameter("origin_lat", originLatitude)
            parameter("origin_lng", originLongitude)
            parameter("destination_lat", destinationLatitude)
            parameter("destination_lng", destinationLongitude)
            parameter("depart_at", departAt.toString())
        }
    }.body()

    suspend fun weather(latitude: Double, longitude: Double, at: Instant): WeatherDto = call {
        http.get("v1/weather") {
            parameter("lat", latitude)
            parameter("lng", longitude)
            parameter("at", at.toString())
        }
    }.body()

    fun close() = http.close()

    private suspend fun call(block: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ProxyException(0, e.message ?: "network")
        }
        if (!response.status.isSuccess()) throw ProxyException(response.status.value, response.status.description)
        return response
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://routealarm-proxy.onrender.com/"
        private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
