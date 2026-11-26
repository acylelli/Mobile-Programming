package com.example.routealarm.shared

/** 기상청 강수형태 */
enum class Precipitation { NONE, RAIN, RAIN_SNOW, SNOW, SHOWER }

/**
 * 날씨에 따른 추가 여유시간. 비·소나기 +5분, 눈·진눈깨비 +10분.
 * 강수형태가 "없음"이면 강수확률이 높아도 시간은 더하지 않고 우산 안내만 한다.
 */
object WeatherRules {
    const val RAIN_EXTRA_MINUTES = 5
    const val SNOW_EXTRA_MINUTES = 10

    /** 이 강수확률 이상이면 비가 안 와도 우산을 챙기라고 알린다. */
    const val UMBRELLA_PROBABILITY = 60

    fun extraMinutes(precipitation: Precipitation): Int = when (precipitation) {
        Precipitation.NONE -> 0
        Precipitation.RAIN, Precipitation.SHOWER -> RAIN_EXTRA_MINUTES
        Precipitation.SNOW, Precipitation.RAIN_SNOW -> SNOW_EXTRA_MINUTES
    }

    fun needsUmbrella(precipitation: Precipitation, probability: Int): Boolean =
        precipitation != Precipitation.NONE || probability >= UMBRELLA_PROBABILITY
}
