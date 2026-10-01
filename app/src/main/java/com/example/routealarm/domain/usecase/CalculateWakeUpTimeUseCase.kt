package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.InvalidInputReason
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * wakeUpTime = recommendedDepartureTime - preparationTime
 */
class CalculateWakeUpTimeUseCase @Inject constructor() {

    operator fun invoke(departure: Instant, preparation: Duration): AppResult<Instant> {
        if (preparation.isNegative) {
            return AppResult.Failure(AppError.InvalidInput(InvalidInputReason.NEGATIVE_DURATION))
        }
        return AppResult.Success(departure.minus(preparation))
    }
}
