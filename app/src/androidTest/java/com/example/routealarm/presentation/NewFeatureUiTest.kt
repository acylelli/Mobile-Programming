package com.example.routealarm.presentation

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.model.PlanWeather
import com.example.routealarm.domain.model.Precipitation
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.ScheduleSound
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.policy.AlarmStatsPolicy
import com.example.routealarm.presentation.common.WeatherStrip
import com.example.routealarm.presentation.schedule.detail.HolidaySkipRow
import com.example.routealarm.presentation.schedule.detail.ScheduleSoundRow
import com.example.routealarm.presentation.settings.AlarmSoundSheet
import com.example.routealarm.presentation.stats.StatsScreen
import com.example.routealarm.presentation.stats.StatsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** 알람음 시트·일정 알람음·공휴일 건너뛰기·날씨 안내·기록 통계 화면을 실제 기기에서 그려 확인한다. */
@RunWith(AndroidJUnit4::class)
class NewFeatureUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun str(@StringRes id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val zone = ZoneId.systemDefault()

    private fun schedule(skipHolidays: Boolean = true, sound: ScheduleSound? = null): Schedule {
        val place = Place(name = "다산역", address = "경기 남양주시", latitude = 37.62, longitude = 127.15, type = PlaceType.CUSTOM)
        return Schedule(
            id = 1, title = "등교", origin = place, destination = place.copy(name = "한성대학교"),
            targetArrivalTime = LocalTime.of(9, 0), preparationMinutes = 30, bufferMinutes = 10,
            repeatDays = DayOfWeek.entries.toSet(), skipHolidays = skipHolidays, sound = sound,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
    }

    // ---- 알람음 시트 ----

    @Test
    fun soundSheet_selectingSoundCallsBack_andShowsVolume() {
        var chosen: AlarmSound? = null
        compose.setContent {
            RouteAlarmTheme {
                AlarmSoundSheet(
                    prefs = UserPreferences(alarmVolumePercent = 70),
                    onSound = { chosen = it }, onCustomSound = { _, _ -> }, onVolume = {}, onGradual = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.settings_volume_format, 70)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.settings_gradual_volume)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.settings_sound_gentle)).performClick()
        assertEquals(AlarmSound.GENTLE, chosen)
    }

    @Test
    fun soundSheet_forSchedule_showsFollowSettingsAndHidesGradual() {
        var followed = false
        compose.setContent {
            RouteAlarmTheme {
                AlarmSoundSheet(
                    prefs = UserPreferences(),
                    onSound = {}, onCustomSound = { _, _ -> }, onVolume = {}, onGradual = {}, onDismiss = {},
                    followSettings = com.example.routealarm.presentation.settings.FollowSettingsOption(active = false, summary = "Sapphire · 70%", onSelect = { followed = true }),
                    showGradual = false,
                )
            }
        }
        compose.onNodeWithText(str(R.string.settings_gradual_volume)).assertDoesNotExist()
        compose.onNodeWithText("Sapphire · 70%").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.schedule_sound_follow)).performClick()
        assertEquals(true, followed)
    }

    @Test
    fun scheduleSoundRow_showsFollowSettingsOrOwnSound() {
        val prefs = UserPreferences(alarmSound = AlarmSound.DEFAULT_ALARM, alarmVolumePercent = 80)
        compose.setContent {
            RouteAlarmTheme {
                androidx.compose.foundation.layout.Column {
                    ScheduleSoundRow(schedule(), prefs) {}
                    ScheduleSoundRow(schedule(sound = ScheduleSound(AlarmSound.GENTLE, volumePercent = 40)), prefs) {}
                }
            }
        }
        val defaultName = str(R.string.settings_sound_default)
        compose.onNodeWithText(str(R.string.schedule_sound_follow_value, str(R.string.settings_sound_value_format, defaultName, 80))).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.settings_sound_value_format, str(R.string.settings_sound_gentle), 40)).assertIsDisplayed()
    }

    // ---- 공휴일 건너뛰기 ----

    @Test
    fun holidayRow_announcesUpcomingHoliday_andToggles() {
        var changed: Boolean? = null
        val before = ZonedDateTime.of(LocalDate.of(2026, 12, 20), LocalTime.of(20, 0), zone).toInstant()
        compose.setContent { RouteAlarmTheme { HolidaySkipRow(schedule(), before) { changed = it } } }
        compose.onNodeWithText(str(R.string.detail_skip_holidays_next, "12월 25일 (금)", "성탄절")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.detail_skip_holidays)).performClick()
        assertEquals(false, changed)
    }

    @Test
    fun holidayRow_whenOff_saysItRingsOnHolidays() {
        compose.setContent { RouteAlarmTheme { HolidaySkipRow(schedule(skipHolidays = false), Instant.EPOCH) {} } }
        compose.onNodeWithText(str(R.string.detail_skip_holidays_off)).assertIsDisplayed()
    }

    // ---- 날씨 ----

    @Test
    fun weatherStrip_showsRainWithExtraTime_andNothingWhenClear() {
        compose.setContent {
            RouteAlarmTheme {
                androidx.compose.foundation.layout.Column {
                    WeatherStrip(PlanWeather(Precipitation.RAIN, 70, 5))
                    WeatherStrip(PlanWeather(Precipitation.NONE, 10, 0))
                }
            }
        }
        // 안내 띠 문구(Paragraph)는 줄바꿈용 문자를 넣어 그리므로 원문은 접근성 설명으로 찾는다.
        compose.onNodeWithContentDescription(str(R.string.weather_strip_extra, str(R.string.weather_rain), 70, 5)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.weather_umbrella_line, str(R.string.weather_chance), 10)).assertDoesNotExist()
    }

    // ---- 기록·통계 ----

    @Test
    fun stats_showsOnTimeRateTilesAndChart() {
        val today = LocalDate.now(zone)
        fun at(daysAgo: Long, minute: Int) = ZonedDateTime.of(today.minusDays(daysAgo), LocalTime.of(7, minute), zone).toInstant()
        val events = listOf(
            AlarmHistoryEvent(scheduleId = 1, type = AlarmEventType.DISMISSED, occurredAt = at(1, 2), newWakeUp = at(1, 0)),
            AlarmHistoryEvent(scheduleId = 1, type = AlarmEventType.DISMISSED, occurredAt = at(2, 12), newWakeUp = at(2, 0)),
            AlarmHistoryEvent(scheduleId = 1, type = AlarmEventType.SNOOZED, occurredAt = at(2, 1), newWakeUp = at(2, 0)),
        )
        val now = ZonedDateTime.of(today, LocalTime.of(21, 0), zone).toInstant()
        val stats = AlarmStatsPolicy.summarize(events, now, zone)
        compose.setContent { RouteAlarmTheme { StatsScreen(StatsUiState(stats), onBack = {}) } }

        compose.onNodeWithTag("stats_on_time_rate").assertTextEquals("50%")
        compose.onNodeWithText(str(R.string.stats_period, 2)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.stats_count_format, 1)).assertIsDisplayed() // 다시 울림 1번
        val yesterday = today.minusDays(2)
        compose.onNodeWithContentDescription(str(R.string.stats_day_late, yesterday.monthValue, yesterday.dayOfMonth, 12)).assertExists()
    }

    @Test
    fun stats_withoutRecords_showsEmptyState() {
        val stats = AlarmStatsPolicy.summarize(emptyList(), Instant.now(), zone)
        compose.setContent { RouteAlarmTheme { StatsScreen(StatsUiState(stats), onBack = {}) } }
        compose.onNodeWithText(str(R.string.stats_empty_title)).assertIsDisplayed()
    }
}
