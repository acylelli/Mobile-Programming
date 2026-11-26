package com.example.routealarm.domain.model

import java.time.Instant

/** 기상청 강수형태 */
enum class Precipitation { NONE, RAIN, RAIN_SNOW, SNOW, SHOWER }

/** 출발 시각이 속한 1시간의 예보 */
data class WeatherForecast(
    val forecastAt: Instant,
    val precipitation: Precipitation,
    /** 강수확률(%) */
    val probability: Int,
    val temperature: Int?,
)

/** 알람 계산에 반영한 날씨. 화면·출발 알림의 "비 예보 · 여유 +5분" 안내에 쓴다. */
data class PlanWeather(
    val precipitation: Precipitation,
    val probability: Int,
    /** 날씨 때문에 더한 여유(분) */
    val extraMinutes: Int,
)
