package com.example.routealarm.domain.repository

import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.ColorTheme
import com.example.routealarm.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun current(): UserPreferences
    suspend fun setDefaultPreparationMinutes(minutes: Int)
    suspend fun setDefaultBufferMinutes(minutes: Int)
    suspend fun setAutoAdjustment(enabled: Boolean)
    suspend fun setTrafficNotifications(enabled: Boolean)
    suspend fun setVibration(enabled: Boolean)
    suspend fun setAlarmSound(sound: AlarmSound)

    /** 휴대폰 벨소리에서 고른 소리를 저장하고 알람음을 CUSTOM 으로 바꾼다. */
    suspend fun setCustomSound(uri: String, title: String?)
    suspend fun setAlarmVolume(percent: Int)
    suspend fun setGradualVolume(enabled: Boolean)
    suspend fun setSnoozeMinutes(minutes: Int)
    suspend fun setColorTheme(theme: ColorTheme)
    suspend fun setLastPaywallPromptDay(epochDay: Long)
    suspend fun setOnboardingCompleted()
}
