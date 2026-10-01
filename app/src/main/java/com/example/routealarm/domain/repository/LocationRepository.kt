package com.example.routealarm.domain.repository

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.GeoPoint

/**
 * 현재 위치는 "필요한 순간에 한 번"만 가져온다. 지속 추적 API 는 의도적으로 제공하지 않는다(배터리/개인정보).
 */
interface LocationRepository {
    fun hasLocationPermission(): Boolean
    suspend fun getCurrentLocation(): AppResult<GeoPoint>
}
