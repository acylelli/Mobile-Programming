package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.InvalidInputReason
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class DepartureTimes(
    val recommendedDeparture: Instant,
    val estimatedArrival: Instant,
)

/**
 * recommendedDepartureTime = targetArrivalTime - travelTime - safetyBuffer
 *
 * Instant(절대 시각)로 계산하는 이유: ZonedDateTime/LocalTime 끼리 빼면 DST 전환일에
 * "벽시계 시간"으로 계산되어 실제 경과시간과 달라질 수 있다. 이동시간은 실제 경과시간이므로
 * 타임라인(Instant) 위에서 빼고, 표시할 때만 사용자 시간대로 변환한다.
 */
class CalculateDepartureTimeUseCase @Inject constructor() {

    operator fun invoke(
        targetArrival: Instant,
        travelTime: Duration,
        safetyBuffer: Duration,
    ): AppResult<DepartureTimes> {
        if (travelTime.isNegative || safetyBuffer.isNegative) {
            return AppResult.Failure(AppError.InvalidInput(InvalidInputReason.NEGATIVE_DURATION))
        }
        val departure = targetArrival.minus(travelTime).minus(safetyBuffer)
        return AppResult.Success(
            DepartureTimes(
                recommendedDeparture = departure,
                estimatedArrival = departure.plus(travelTime),
            ),
        )
    }
}
