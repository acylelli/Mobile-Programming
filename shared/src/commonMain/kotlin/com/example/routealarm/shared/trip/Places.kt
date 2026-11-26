package com.example.routealarm.shared.trip

import com.example.routealarm.shared.api.ProxyClient
import kotlinx.coroutines.CancellationException

/** 장소 검색(카카오 로컬, 서버 경유). 실패하면 error 에 이유 */
data class PlaceSearchResult(val places: List<TripPlace>, val error: String?)

class PlaceSearch(private val client: ProxyClient = ProxyClient()) {
    @Throws(CancellationException::class)
    suspend fun search(query: String): PlaceSearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return PlaceSearchResult(emptyList(), null)
        return try {
            PlaceSearchResult(
                client.searchPlaces(trimmed).map { TripPlace(it.name, it.address, it.latitude, it.longitude, it.lines) },
                null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PlaceSearchResult(emptyList(), "검색하지 못했어요. 인터넷 연결을 확인해 주세요.")
        }
    }
}
