package com.example.routealarm.domain.usecase

import com.example.routealarm.shared.AlarmEngine
import java.time.Duration
import javax.inject.Inject
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/**
 * safetyBuffer = 기본 여유시간 + 지연의 30%(최대 10분) + (실시간 아님) 5분.
 * 계산은 shared 모듈의 [AlarmEngine.safetyBuffer] 가 한다.
 */
class CalculateSafetyBufferUseCase @Inject constructor() {
    operator fun invoke(baseBufferMinutes: Int, delay: Duration, isRealtime: Boolean): Duration =
        AlarmEngine.safetyBuffer(baseBufferMinutes, delay.toKotlinDuration(), isRealtime).toJavaDuration()
}
