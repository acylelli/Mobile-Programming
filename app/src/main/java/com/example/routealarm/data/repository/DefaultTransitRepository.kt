package com.example.routealarm.data.repository

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.data.local.db.RouteCacheDao
import com.example.routealarm.data.local.db.RouteCacheEntity
import com.example.routealarm.data.mapper.StoredRoute
import com.example.routealarm.data.mapper.TransitDtoMapper
import com.example.routealarm.data.mapper.toDomain
import com.example.routealarm.data.mapper.toStored
import com.example.routealarm.data.remote.TransitRemoteDataSource
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.RouteQueryResult
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.domain.repository.MetricsRepository
import com.example.routealarm.domain.repository.TransitRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Offline First 교통 저장소.
 *
 * 1. 원격 조회 성공 → Domain 변환 → Room 캐시 저장 → isRealtime = true
 * 2. 네트워크/서버 실패 → 같은 출발·도착 캐시가 있으면 isRealtime = false 로 반환 (알람은 유지됨)
 * 3. 캐시도 없으면 AppError 반환. UseCase 는 마지막 계산값으로 알람을 유지한다.
 *
 * 캐시 키는 좌표를 약 100m 단위로 반올림해 만든다. 정확한 좌표를 저장하지 않으면서도
 * "현재 위치"가 조금 흔들려도 같은 캐시를 재사용할 수 있다.
 */
@Singleton
class DefaultTransitRepository @Inject constructor(
    private val remote: TransitRemoteDataSource,
    private val routeCacheDao: RouteCacheDao,
    private val metricsRepository: MetricsRepository,
    private val timeProvider: TimeProvider,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TransitRepository {

    override suspend fun getRoutes(
        origin: Place,
        destination: Place,
        departureTime: Instant,
    ): AppResult<RouteQueryResult> = withContext(ioDispatcher) {
        val key = cacheKey(origin, destination)
        val started = System.nanoTime()
        try {
            val response = remote.fetchRoutes(origin, destination, departureTime)
            metricsRepository.record(MetricType.TRANSIT_API_CALL, (System.nanoTime() - started) / NANOS_PER_MILLI)
            val routes = TransitDtoMapper.toDomain(response)
            if (routes.isEmpty()) return@withContext fallback(key, AppError.NoRoute)

            val now = timeProvider.now()
            runCatching { saveCache(key, routes, now) }
            AppResult.Success(RouteQueryResult(routes, isRealtime = response.realtime, fetchedAt = now))
        } catch (e: IOException) {
            metricsRepository.record(MetricType.TRANSIT_API_FAILURE)
            fallback(key, AppError.Network)
        } catch (e: HttpException) {
            metricsRepository.record(MetricType.TRANSIT_API_FAILURE)
            fallback(key, AppError.Server(e.code()))
        } catch (e: IllegalArgumentException) {
            // 직렬화 형식 불일치 등 — 서버 계약이 깨진 경우
            metricsRepository.record(MetricType.TRANSIT_API_FAILURE)
            fallback(key, AppError.Unknown(e.message))
        }
    }

    private suspend fun fallback(key: String, error: AppError): AppResult<RouteQueryResult> {
        val cached = runCatching { routeCacheDao.get(key) }.getOrNull() ?: return AppResult.Failure(error)
        val fetchedAt = Instant.ofEpochMilli(cached.fetchedAt)
        if (Duration.between(fetchedAt, timeProvider.now()) > CACHE_MAX_AGE) return AppResult.Failure(error)
        val routes = runCatching {
            json.decodeFromString(ListSerializer(StoredRoute.serializer()), cached.routesJson).map { it.toDomain() }
        }.getOrNull() ?: return AppResult.Failure(error)
        metricsRepository.record(MetricType.TRANSIT_CACHE_HIT)
        return AppResult.Success(RouteQueryResult(routes, isRealtime = false, fetchedAt = fetchedAt))
    }

    private suspend fun saveCache(key: String, routes: List<TransitRoute>, now: Instant) {
        val payload = json.encodeToString(ListSerializer(StoredRoute.serializer()), routes.map { it.toStored() })
        routeCacheDao.upsert(RouteCacheEntity(key, payload, now.toEpochMilli()))
        routeCacheDao.deleteOlderThan(now.minus(CACHE_MAX_AGE).toEpochMilli())
    }

    private fun cacheKey(origin: Place, destination: Place): String =
        "${coarse(origin.latitude)},${coarse(origin.longitude)}->${coarse(destination.latitude)},${coarse(destination.longitude)}"

    private fun coarse(coordinate: Double): Int = (coordinate * COORDINATE_SCALE).roundToInt()

    companion object {
        /** 하루 지난 경로 캐시는 신뢰하지 않는다. */
        val CACHE_MAX_AGE: Duration = Duration.ofHours(24)
        private const val COORDINATE_SCALE = 1_000 // 0.001도 ≈ 100m
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
