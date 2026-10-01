package com.example.routealarm.domain.model

data class GeoPoint(val latitude: Double, val longitude: Double)

enum class PlaceType { HOME, SCHOOL, WORK, CUSTOM }

/**
 * 출발지/도착지. 저장된 즐겨찾기 장소(id > 0)일 수도, 검색 결과(id = 0)일 수도 있다.
 */
data class Place(
    val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: PlaceType = PlaceType.CUSTOM,
    val isDemo: Boolean = false,
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)

    fun isSameLocationAs(other: Place): Boolean =
        kotlin.math.abs(latitude - other.latitude) < SAME_LOCATION_EPSILON &&
            kotlin.math.abs(longitude - other.longitude) < SAME_LOCATION_EPSILON

    private companion object {
        /** 약 10m. 이보다 가까우면 같은 장소로 본다. */
        const val SAME_LOCATION_EPSILON = 0.0001
    }
}
