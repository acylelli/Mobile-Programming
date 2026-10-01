package com.example.routealarm.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.routealarm.data.mapper.enumValueOrDefault
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.DemoSettings
import com.example.routealarm.domain.model.ThemeMode
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
        val SNOOZE = intPreferencesKey("snooze_minutes")
        val THEME = stringPreferencesKey("theme_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val DEMO_SEEDED = booleanPreferencesKey("demo_seeded")
        val DEMO_EXTRA_DELAY = intPreferencesKey("demo_extra_delay_minutes")
        val DEMO_NETWORK_FAILURE = booleanPreferencesKey("demo_network_failure")
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
        snoozeMinutes = this[Keys.SNOOZE] ?: UserPreferences.DEFAULT_SNOOZE_MINUTES,
        themeMode = enumValueOrDefault(this[Keys.THEME] ?: "", ThemeMode.SYSTEM),
        onboardingCompleted = this[Keys.ONBOARDING_DONE] ?: false,
        demoSeeded = this[Keys.DEMO_SEEDED] ?: false,
        demo = DemoSettings(
            extraDelayMinutes = this[Keys.DEMO_EXTRA_DELAY] ?: 0,
            simulateNetworkFailure = this[Keys.DEMO_NETWORK_FAILURE] ?: false,
        ),
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
    override suspend fun setSnoozeMinutes(minutes: Int) = set(Keys.SNOOZE, minutes)
    override suspend fun setThemeMode(mode: ThemeMode) = set(Keys.THEME, mode.name)
    override suspend fun setOnboardingCompleted() = set(Keys.ONBOARDING_DONE, true)
    override suspend fun setDemoSeeded() = set(Keys.DEMO_SEEDED, true)
    override suspend fun setDemoExtraDelay(minutes: Int) = set(Keys.DEMO_EXTRA_DELAY, minutes)
    override suspend fun setDemoNetworkFailure(enabled: Boolean) = set(Keys.DEMO_NETWORK_FAILURE, enabled)
}
