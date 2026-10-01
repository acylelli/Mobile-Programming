package com.example.routealarm.domain.model

import java.time.Duration

/**
 * 홈 화면 Status Chip 에 표시되는 교통 상태.
 * 색상만으로 상태를 전달하지 않도록 UI 에서는 항상 텍스트를 함께 보여준다.
 */
sealed interface TrafficStatus {
    data object Normal : TrafficStatus
    data class MinorDelay(val delayMinutes: Int) : TrafficStatus
    data class SevereDelay(val delayMinutes: Int) : TrafficStatus

    companion object {
        const val MINOR_DELAY_THRESHOLD_MINUTES = 3
        const val SEVERE_DELAY_THRESHOLD_MINUTES = 15

        fun from(delay: Duration): TrafficStatus {
            val minutes = delay.toMinutes().toInt()
            return when {
                minutes >= SEVERE_DELAY_THRESHOLD_MINUTES -> SevereDelay(minutes)
                minutes >= MINOR_DELAY_THRESHOLD_MINUTES -> MinorDelay(minutes)
                else -> Normal
            }
        }
    }
}
