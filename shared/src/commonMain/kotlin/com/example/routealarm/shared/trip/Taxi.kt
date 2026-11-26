package com.example.routealarm.shared.trip

import com.example.routealarm.shared.api.ProxyClient
import kotlinx.coroutines.CancellationException

/** 택시 예상(카카오 모빌리티, 서버 경유) */
data class TaxiEstimate(val minutes: Int, val fareWon: Int)

class TaxiService(private val client: ProxyClient = ProxyClient()) {
    /** 지각 위험일 때만 부른다. 실패하면 null(카드는 경고와 호출 버튼만 보여 준다) */
    @Throws(CancellationException::class)
    suspend fun estimate(origin: TripPlace, destination: TripPlace): TaxiEstimate? = try {
        client.taxi(origin.latitude, origin.longitude, destination.latitude, destination.longitude).let { TaxiEstimate(it.minutes, it.fareWon) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
