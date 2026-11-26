package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.PlanWeather
import com.example.routealarm.domain.model.Precipitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherPolicyTest {

    @Test
    fun extraMinutes_rainFiveSnowTen() {
        assertEquals(0, WeatherPolicy.extraMinutes(Precipitation.NONE))
        assertEquals(5, WeatherPolicy.extraMinutes(Precipitation.RAIN))
        assertEquals(5, WeatherPolicy.extraMinutes(Precipitation.SHOWER))
        assertEquals(10, WeatherPolicy.extraMinutes(Precipitation.SNOW))
        assertEquals(10, WeatherPolicy.extraMinutes(Precipitation.RAIN_SNOW))
    }

    @Test
    fun umbrella_whenPrecipitatingOrHighProbability() {
        assertTrue(WeatherPolicy.needsUmbrella(PlanWeather(Precipitation.RAIN, 30, 5)))
        assertTrue(WeatherPolicy.needsUmbrella(PlanWeather(Precipitation.NONE, 60, 0)))
        assertFalse(WeatherPolicy.needsUmbrella(PlanWeather(Precipitation.NONE, 40, 0)))
        assertFalse(WeatherPolicy.isNotable(null))
    }
}
