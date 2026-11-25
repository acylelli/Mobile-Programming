package com.example.routealarm.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.routealarm.data.mapper.enumValueOrDefault
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.ColorTheme
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 작은 사용자 설정은 Room 이 아니라 Preferences DataStore 에 둔다.
 * 설계 이유: 스키마/마이그레이션이 필요 없고, Flow 로 변경을 바로 구독할 수 있으며, 트랜잭션이 원자적이다.
 */
@Singleton
class DataStorePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    private object Keys {
        val PREPARATION = intPreferencesKey("default_preparation_minutes")
        val BUFFER = intPreferencesKey("default_buffer_minutes")
        val AUTO_ADJUST = booleanPreferencesKey("auto_adjustment")
        val TRAFFIC_NOTIFICATIONS = booleanPreferencesKey("traffic_notifications")
        val VIBRATION = booleanPreferencesKey("vibration")
        val ALARM_SOUND = stringPreferencesKey("alarm_sound")
        val CUSTOM_SOUND_URI = stringPreferencesKey("custom_sound_uri")
        val CUSTOM_SOUND_TITLE = stringPreferencesKey("custom_sound_title")
        val ALARM_VOLUME = intPreferencesKey("alarm_volume_percent")
        val GRADUAL_VOLUME = booleanPreferencesKey("gradual_volume")
        val SNOOZE = intPreferencesKey("snooze_minutes")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val PAYWALL_PROMPT_DAY = longPreferencesKey("paywall_prompt_epoch_day")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
    }

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toModel() }

    override suspend fun current(): UserPreferences = preferences.first()

    private fun Preferences.toModel() = UserPreferences(
        defaultPreparationMinutes = this[Keys.PREPARATION] ?: UserPreferences.DEFAULT_PREPARATION_MINUTES,
        defaultBufferMinutes = this[Keys.BUFFER] ?: UserPreferences.DEFAULT_BUFFER_MINUTES,
        autoAdjustment = this[Keys.AUTO_ADJUST] ?: true,
        trafficNotificationsEnabled = this[Keys.TRAFFIC_NOTIFICATIONS] ?: true,
        vibrationEnabled = this[Keys.VIBRATION] ?: true,
        alarmSound = enumValueOrDefault(this[Keys.ALARM_SOUND] ?: "", AlarmSound.DEFAULT_ALARM),
        customSoundUri = this[Keys.CUSTOM_SOUND_URI],
        customSoundTitle = this[Keys.CUSTOM_SOUND_TITLE],
        alarmVolumePercent = (this[Keys.ALARM_VOLUME] ?: UserPreferences.DEFAULT_ALARM_VOLUME_PERCENT)
            .coerceIn(UserPreferences.ALARM_VOLUME_RANGE),
        gradualVolume = this[Keys.GRADUAL_VOLUME] ?: false,
        snoozeMinutes = this[Keys.SNOOZE] ?: UserPreferences.DEFAULT_SNOOZE_MINUTES,
        colorTheme = enumValueOrDefault(this[Keys.COLOR_THEME] ?: "", ColorTheme.DEFAULT),
        lastPaywallPromptEpochDay = this[Keys.PAYWALL_PROMPT_DAY],
        onboardingCompleted = this[Keys.ONBOARDING_DONE] ?: false,
    )

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    override suspend fun setDefaultPreparationMinutes(minutes: Int) = set(Keys.PREPARATION, minutes)
    override suspend fun setDefaultBufferMinutes(minutes: Int) = set(Keys.BUFFER, minutes)
    override suspend fun setAutoAdjustment(enabled: Boolean) = set(Keys.AUTO_ADJUST, enabled)
    override suspend fun setTrafficNotifications(enabled: Boolean) = set(Keys.TRAFFIC_NOTIFICATIONS, enabled)
    override suspend fun setVibration(enabled: Boolean) = set(Keys.VIBRATION, enabled)
    override suspend fun setAlarmSound(sound: AlarmSound) = set(Keys.ALARM_SOUND, sound.name)
    override suspend fun setCustomSound(uri: String, title: String?) {
        dataStore.edit {
            it[Keys.ALARM_SOUND] = AlarmSound.CUSTOM.name
            it[Keys.CUSTOM_SOUND_URI] = uri
            if (title != null) it[Keys.CUSTOM_SOUND_TITLE] = title else it.remove(Keys.CUSTOM_SOUND_TITLE)
        }
    }
    override suspend fun setAlarmVolume(percent: Int) = set(Keys.ALARM_VOLUME, percent.coerceIn(UserPreferences.ALARM_VOLUME_RANGE))
    override suspend fun setGradualVolume(enabled: Boolean) = set(Keys.GRADUAL_VOLUME, enabled)
    override suspend fun setSnoozeMinutes(minutes: Int) = set(Keys.SNOOZE, minutes)
    override suspend fun setColorTheme(theme: ColorTheme) = set(Keys.COLOR_THEME, theme.name)
    override suspend fun setLastPaywallPromptDay(epochDay: Long) = set(Keys.PAYWALL_PROMPT_DAY, epochDay)
    override suspend fun setOnboardingCompleted() = set(Keys.ONBOARDING_DONE, true)
}
