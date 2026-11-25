package com.example.routealarm.domain.model

/** 지금 출발 기준 택시 예상(카카오모빌리티 자동차 길찾기) */
data class TaxiEstimate(
    val minutes: Int,
    val distanceMeters: Int,
    /** 예상 택시 요금(원), 통행료 별도 */
    val fareWon: Int,
    val tollWon: Int = 0,
)
