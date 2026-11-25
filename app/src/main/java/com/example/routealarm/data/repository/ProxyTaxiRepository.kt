package com.example.routealarm.data.repository

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.data.remote.proxy.RouteAlarmProxyApi
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.TaxiEstimate
import com.example.routealarm.domain.repository.TaxiRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Proxy 서버의 택시 예상(카카오모빌리티 키는 서버에만 있다). 실패해도 화면은 택시 안내 없이 지각 경고만 보여 준다. */
@Singleton
class ProxyTaxiRepository @Inject constructor(
    private val api: RouteAlarmProxyApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TaxiRepository {
    override suspend fun estimate(origin: Place, destination: Place): AppResult<TaxiEstimate> = withContext(ioDispatcher) {
        try {
            val dto = api.estimateTaxi(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
            AppResult.Success(TaxiEstimate(dto.minutes, dto.distanceMeters, dto.fareWon, dto.tollWon))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (e: HttpException) {
            AppResult.Failure(AppError.Server(e.code()))
        }
    }
}
