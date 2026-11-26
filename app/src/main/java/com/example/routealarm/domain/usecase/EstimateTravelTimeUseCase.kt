package com.example.routealarm.domain.usecase

import com.example.routealarm.shared.AlarmEngine
import java.time.Duration
import javax.inject.Inject
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/**
 * 계산에 사용할 이동시간. 현재 ETA 와 최근 이동시간 평균을 섞되 현재 ETA 보다 짧아지지 않게 한다.
 * 계산은 shared 모듈의 [AlarmEngine.estimateTravel] 이 한다.
 */
class EstimateTravelTimeUseCase @Inject constructor() {
    operator fun invoke(current: Duration, recent: List<Duration> = emptyList()): Duration =
        AlarmEngine.estimateTravel(current.toKotlinDuration(), recent.map { it.toKotlinDuration() }).toJavaDuration()
}
