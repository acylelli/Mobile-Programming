package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.PlanWeather
import com.example.routealarm.domain.model.Precipitation
import com.example.routealarm.domain.model.WeatherForecast
import com.example.routealarm.shared.WeatherRules
import com.example.routealarm.shared.Precipitation as SharedPrecipitation

/**
 * 날씨에 따른 추가 여유시간.
 * 비·소나기는 걷는 속도가 느려지고 버스가 밀리므로 +5분, 눈·진눈깨비는 길이 미끄럽고 정체가 심해 +10분.
 * 강수형태가 "없음"이면 강수확률이 높아도 시간은 더하지 않고 우산 안내만 한다.
 * 규칙은 shared 모듈의 [WeatherRules] 에 있다.
 */
object WeatherPolicy {
    const val RAIN_EXTRA_MINUTES = WeatherRules.RAIN_EXTRA_MINUTES
    const val SNOW_EXTRA_MINUTES = WeatherRules.SNOW_EXTRA_MINUTES
    /** 이 강수확률 이상이면 비가 안 와도 우산을 챙기라고 알린다. */
    const val UMBRELLA_PROBABILITY = WeatherRules.UMBRELLA_PROBABILITY

    fun extraMinutes(precipitation: Precipitation): Int = WeatherRules.extraMinutes(precipitation.toShared())

    fun toPlanWeather(forecast: WeatherForecast): PlanWeather =
        PlanWeather(forecast.precipitation, forecast.probability, extraMinutes(forecast.precipitation))

    fun needsUmbrella(weather: PlanWeather): Boolean =
        WeatherRules.needsUmbrella(weather.precipitation.toShared(), weather.probability)

    /** 화면에 보여 줄 만한 날씨인지(맑은 날은 아무것도 띄우지 않는다) */
    fun isNotable(weather: PlanWeather?): Boolean = weather != null && needsUmbrella(weather)

    private fun Precipitation.toShared(): SharedPrecipitation = SharedPrecipitation.valueOf(name)
}
