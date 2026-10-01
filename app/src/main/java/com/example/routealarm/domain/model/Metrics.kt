package com.example.routealarm.domain.model

/**
 * 포트폴리오용 측정 지표. 개인 식별 정보 없이 횟수/시간만 기록한다.
 */
enum class MetricType {
    TRANSIT_API_CALL,
    TRANSIT_API_FAILURE,
    TRANSIT_CACHE_HIT,
    ALARM_RECALCULATION,
    ALARM_AUTO_ADJUSTED,
    REFRESH_WORK_RUN,
}

data class MetricsSummary(
    val apiCallCount: Int = 0,
    val apiFailureCount: Int = 0,
    val averageApiLatencyMillis: Long? = null,
    val cacheHitCount: Int = 0,
    val recalculationCount: Int = 0,
    val autoAdjustedCount: Int = 0,
    val refreshWorkRunCount: Int = 0,
) {
    /** 네트워크 실패 시 캐시로 응답한 비율 */
    val cacheHitRate: Float?
        get() {
            val total = apiFailureCount
            return if (total == 0) null else cacheHitCount.toFloat() / total
        }
}
