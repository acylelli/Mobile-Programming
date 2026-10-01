package com.example.routealarm.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.GeoPoint
import com.example.routealarm.domain.repository.LocationRepository
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FusedLocationProviderClient 로 현재 위치를 "한 번만" 가져온다.
 *
 * 배터리 설계: 지속적인 위치 업데이트(requestLocationUpdates)는 쓰지 않는다. 일정 생성/갱신 순간에
 * getCurrentLocation 으로 1회 조회하고, 그마저도 BALANCED 정확도(Wi-Fi/셀 기반, ~100m)면 충분하다.
 * 가져온 좌표는 경로 계산에만 쓰고 로그/DB 에 남기지 않는다.
 */
@Singleton
class FusedLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationRepository {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun getCurrentLocation(): AppResult<GeoPoint> {
        if (!hasLocationPermission()) return AppResult.Failure(AppError.LocationPermissionDenied)
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(MAX_AGE_MILLIS)
                .setDurationMillis(TIMEOUT_MILLIS)
                .build()
            @Suppress("MissingPermission")
            val location = withTimeoutOrNull(TIMEOUT_MILLIS + GRACE_MILLIS) {
                client.getCurrentLocation(request, CancellationTokenSource().token).await()
            }
            if (location == null) {
                AppResult.Failure(AppError.LocationUnavailable)
            } else {
                AppResult.Success(GeoPoint(location.latitude, location.longitude))
            }
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.LocationPermissionDenied)
        } catch (e: Exception) {
            AppResult.Failure(AppError.LocationUnavailable)
        }
    }

    private companion object {
        /** 10분 이내의 캐시된 위치면 새로 측정하지 않는다. */
        const val MAX_AGE_MILLIS = 10L * 60 * 1000
        const val TIMEOUT_MILLIS = 15_000L
        const val GRACE_MILLIS = 2_000L
    }
}
