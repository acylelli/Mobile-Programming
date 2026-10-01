package com.example.routealarm.domain.policy

/**
 * 안전 여유시간 계산 상수.
 * safetyBuffer = 기본 여유시간 + 지연에 비례한 추가 여유 + (실시간 데이터가 아니면) 불확실성 여유
 */
object BufferPolicy {
    /** 지연이 발생하면 그 지연이 더 커질 수 있으므로 지연의 30% 를 추가 여유로 둔다. */
    const val DELAY_BUFFER_RATIO = 0.3

    /** 지연 기반 추가 여유의 상한. 과도하게 일찍 깨우지 않기 위함 */
    const val MAX_DELAY_EXTRA_MINUTES = 10L

    /** 캐시 데이터로 계산할 때(실시간 아님) 추가하는 여유 */
    const val NON_REALTIME_EXTRA_MINUTES = 5L
}

/**
 * 실시간 ETA 하나만 믿지 않기 위한 이동시간 안정화 상수.
 */
object TravelEstimatePolicy {
    const val CURRENT_WEIGHT = 0.7
    const val HISTORY_WEIGHT = 0.3
    const val HISTORY_SAMPLE_SIZE = 5

    /** 이동시간을 전혀 모를 때(첫 오프라인 계산) 사용하는 기본값 */
    const val FALLBACK_TRAVEL_MINUTES = 60L
}
