package com.example.routealarm.domain.repository

import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.ThemeMode
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
    suspend fun setSnoozeMinutes(minutes: Int)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOnboardingCompleted()
    suspend fun setDemoSeeded()
    suspend fun setDemoExtraDelay(minutes: Int)
    suspend fun setDemoNetworkFailure(enabled: Boolean)
}
