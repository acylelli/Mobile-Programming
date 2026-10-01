package com.example.routealarm.domain.usecase

import com.example.routealarm.domain.policy.TravelEstimatePolicy
import java.time.Duration
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max

/**
 * 계산에 사용할 이동시간을 정한다.
 *
 * 설계 이유: 실시간 ETA 한 번만 믿으면 우연히 빠르게 나온 값 때문에 알람이 늦게 잡힐 수 있다.
 * 최근 계산값의 평균과 가중 평균을 내되, 결과는 절대 현재 ETA 보다 짧아지지 않게 한다
 * (평균이 더 짧다고 현재 지연을 무시하면 지각한다). 과거 데이터가 쌓이면 이 지점이 개인화의 확장 포인트다.
 *
 * 분 단위 올림: 1분 일찍 깨우는 것이 1분 늦게 깨우는 것보다 낫다.
 */
class EstimateTravelTimeUseCase @Inject constructor() {

    operator fun invoke(current: Duration, recent: List<Duration> = emptyList()): Duration {
        val currentMinutes = current.toMillis().coerceAtLeast(0) / MILLIS_PER_MINUTE
        val validRecent = recent.filterNot { it.isNegative || it.isZero }
        if (validRecent.isEmpty()) return ceilMinutes(currentMinutes)

        val averageMinutes = validRecent.map { it.toMillis() / MILLIS_PER_MINUTE }.average()
        val blended = currentMinutes * TravelEstimatePolicy.CURRENT_WEIGHT +
            averageMinutes * TravelEstimatePolicy.HISTORY_WEIGHT
        return ceilMinutes(max(currentMinutes, blended))
    }

    private fun ceilMinutes(minutes: Double): Duration = Duration.ofMinutes(ceil(minutes - EPSILON).toLong())

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000.0

        /** 부동소수점 오차로 60.0000001 이 61분이 되는 것을 막는다. */
        const val EPSILON = 1e-6
    }
}
