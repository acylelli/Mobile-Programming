package com.example.routealarm.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AlarmSound { DEFAULT_ALARM, GENTLE, SILENT }

/** DataStore 에 저장되는 작은 사용자 설정 */
data class UserPreferences(
    val defaultPreparationMinutes: Int = DEFAULT_PREPARATION_MINUTES,
    val defaultBufferMinutes: Int = DEFAULT_BUFFER_MINUTES,
    val autoAdjustment: Boolean = true,
    val trafficNotificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val alarmSound: AlarmSound = AlarmSound.DEFAULT_ALARM,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val demoSeeded: Boolean = false,
    val demo: DemoSettings = DemoSettings(),
) {
    companion object {
        const val DEFAULT_PREPARATION_MINUTES = 30
        const val DEFAULT_BUFFER_MINUTES = 10
        const val DEFAULT_SNOOZE_MINUTES = 5
        val SNOOZE_OPTIONS = listOf(5, 10, 15)
    }
}

/**
 * API 키 없이 "교통 상황 변화 → 알람 자동 조정" 흐름을 시연하기 위한 설정.
 * Fake 교통 데이터 소스만 이 값을 읽는다. 실제 Proxy 사용 시 무시된다.
 */
data class DemoSettings(
    val extraDelayMinutes: Int = 0,
    val simulateNetworkFailure: Boolean = false,
)
