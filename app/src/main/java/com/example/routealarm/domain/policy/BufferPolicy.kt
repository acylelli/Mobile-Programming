package com.example.routealarm.domain.policy

import com.example.routealarm.shared.AlarmEngine

/**
 * 안전 여유시간 계산 상수(값은 shared 모듈의 [AlarmEngine] 에 있다).
 * safetyBuffer = 기본 여유시간 + 지연에 비례한 추가 여유 + (실시간 데이터가 아니면) 불확실성 여유
 */
object BufferPolicy {
    const val DELAY_BUFFER_RATIO = AlarmEngine.DELAY_BUFFER_RATIO
    const val MAX_DELAY_EXTRA_MINUTES = AlarmEngine.MAX_DELAY_EXTRA_MINUTES
    const val NON_REALTIME_EXTRA_MINUTES = AlarmEngine.NON_REALTIME_EXTRA_MINUTES
}

/** 실시간 ETA 하나만 믿지 않기 위한 이동시간 안정화 상수 */
object TravelEstimatePolicy {
    const val CURRENT_WEIGHT = AlarmEngine.CURRENT_WEIGHT
    const val HISTORY_WEIGHT = AlarmEngine.HISTORY_WEIGHT
    const val HISTORY_SAMPLE_SIZE = AlarmEngine.HISTORY_SAMPLE_SIZE

    /** 이동시간을 전혀 모를 때(첫 오프라인 계산) 사용하는 기본값 */
    const val FALLBACK_TRAVEL_MINUTES = AlarmEngine.FALLBACK_TRAVEL_MINUTES
}
