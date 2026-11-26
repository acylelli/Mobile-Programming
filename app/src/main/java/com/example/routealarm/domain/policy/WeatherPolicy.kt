package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.PlanWeather
import com.example.routealarm.domain.model.Precipitation
import com.example.routealarm.domain.model.WeatherForecast

/**
 * 날씨에 따른 추가 여유시간.
 * 비·소나기는 걷는 속도가 느려지고 버스가 밀리므로 +5분, 눈·진눈깨비는 길이 미끄럽고 정체가 심해 +10분.
 * 강수형태가 "없음"이면 강수확률이 높아도 시간은 더하지 않고 우산 안내만 한다.
 */
object WeatherPolicy {
    const val RAIN_EXTRA_MINUTES = 5
    const val SNOW_EXTRA_MINUTES = 10
    /** 이 강수확률 이상이면 비가 안 와도 우산을 챙기라고 알린다. */
    const val UMBRELLA_PROBABILITY = 60

    fun extraMinutes(precipitation: Precipitation): Int = when (precipitation) {
        Precipitation.NONE -> 0
        Precipitation.RAIN, Precipitation.SHOWER -> RAIN_EXTRA_MINUTES
        Precipitation.SNOW, Precipitation.RAIN_SNOW -> SNOW_EXTRA_MINUTES
    }

    fun toPlanWeather(forecast: WeatherForecast): PlanWeather =
        PlanWeather(forecast.precipitation, forecast.probability, extraMinutes(forecast.precipitation))

    fun needsUmbrella(weather: PlanWeather): Boolean =
        weather.precipitation != Precipitation.NONE || weather.probability >= UMBRELLA_PROBABILITY

    /** 화면에 보여 줄 만한 날씨인지(맑은 날은 아무것도 띄우지 않는다) */
    fun isNotable(weather: PlanWeather?): Boolean = weather != null && needsUmbrella(weather)
}
