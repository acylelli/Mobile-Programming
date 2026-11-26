package com.example.routealarm.data.repository

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.data.mapper.enumValueOrDefault
import com.example.routealarm.data.remote.proxy.RouteAlarmProxyApi
import com.example.routealarm.domain.model.Precipitation
import com.example.routealarm.domain.model.WeatherForecast
import com.example.routealarm.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Proxy 서버의 기상청 단기예보. 실패하면 날씨 없이(여유 추가 없이) 계산한다. */
@Singleton
class ProxyWeatherRepository @Inject constructor(
    private val api: RouteAlarmProxyApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WeatherRepository {
    override suspend fun forecast(latitude: Double, longitude: Double, at: Instant): AppResult<WeatherForecast> =
        withContext(ioDispatcher) {
            try {
                val dto = api.weather(latitude, longitude, at.toString())
                AppResult.Success(
                    WeatherForecast(
                        forecastAt = OffsetDateTime.parse(dto.forecastAt).toInstant(),
                        precipitation = enumValueOrDefault(dto.precipitation, Precipitation.NONE),
                        probability = dto.probability,
                        temperature = dto.temperature,
                    ),
                )
            } catch (e: IOException) {
                AppResult.Failure(AppError.Network)
            } catch (e: HttpException) {
                AppResult.Failure(AppError.Server(e.code()))
            } catch (e: java.time.format.DateTimeParseException) {
                AppResult.Failure(AppError.Server(0))
            }
        }
}
