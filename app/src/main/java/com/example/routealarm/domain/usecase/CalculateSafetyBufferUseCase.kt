package com.example.routealarm.domain.usecase

import com.example.routealarm.domain.policy.BufferPolicy
import java.time.Duration
import javax.inject.Inject
import kotlin.math.ceil

/**
 * safetyBuffer = 기본 여유시간 + 교통 변동에 따른 추가 여유시간
 */
class CalculateSafetyBufferUseCase @Inject constructor() {

    operator fun invoke(baseBufferMinutes: Int, delay: Duration, isRealtime: Boolean): Duration {
        val base = baseBufferMinutes.coerceAtLeast(0).toLong()

        val delayMinutes = delay.toMinutes().coerceAtLeast(0)
        val delayExtra = ceil(delayMinutes * BufferPolicy.DELAY_BUFFER_RATIO).toLong()
            .coerceAtMost(BufferPolicy.MAX_DELAY_EXTRA_MINUTES)

        // 캐시 데이터는 그 사이 상황이 바뀌었을 수 있으므로 불확실성만큼 여유를 더 둔다.
        val uncertaintyExtra = if (isRealtime) 0L else BufferPolicy.NON_REALTIME_EXTRA_MINUTES

        return Duration.ofMinutes(base + delayExtra + uncertaintyExtra)
    }
}
