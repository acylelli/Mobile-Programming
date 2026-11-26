package com.example.routealarm.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.NoticeStrip
import com.example.routealarm.domain.model.PlanWeather
import com.example.routealarm.domain.model.Precipitation
import com.example.routealarm.domain.policy.WeatherPolicy

/** 강수형태 이름. "없음"인데 강수확률이 높으면 "비 소식" */
fun Precipitation.labelRes(): Int = when (this) {
    Precipitation.NONE -> R.string.weather_chance
    Precipitation.RAIN -> R.string.weather_rain
    Precipitation.SHOWER -> R.string.weather_shower
    Precipitation.SNOW -> R.string.weather_snow
    Precipitation.RAIN_SNOW -> R.string.weather_rain_snow
}

/**
 * 출발 시각에 비·눈 예보가 있으면 보여 주는 안내 띠. "비 예보 70% · 여유 +5분 더 뒀어요 · 우산 챙기세요"
 * 맑은 날(또는 날씨를 모를 때)은 아무것도 그리지 않는다.
 */
@Composable
fun WeatherStrip(weather: PlanWeather?, modifier: Modifier = Modifier) {
    if (weather == null || !WeatherPolicy.isNotable(weather)) return
    val label = stringResource(weather.precipitation.labelRes())
    NoticeStrip(
        text = if (weather.extraMinutes > 0) {
            stringResource(R.string.weather_strip_extra, label, weather.probability, weather.extraMinutes)
        } else {
            stringResource(R.string.weather_umbrella_line, label, weather.probability)
        },
        icon = if (weather.precipitation == Precipitation.SNOW || weather.precipitation == Precipitation.RAIN_SNOW) Icons.Outlined.AcUnit else Icons.Outlined.Umbrella,
        modifier = modifier,
    )
}
