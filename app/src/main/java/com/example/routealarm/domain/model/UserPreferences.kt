package com.example.routealarm.domain.model

/**
 * 컬러 테마(UI-F-002). 기본(오렌지)·새벽·나이트 3종이며 플랜과 관계없이 고를 수 있다.
 * 화면 모드(라이트/다크)는 따로 고르지 않는다. 기기 설정을 따르고, 나이트만 항상 어두운 화면이다.
 * 지하철 노선색·상태색 같은 정보 색은 테마와 관계없이 고정된다.
 */
enum class ColorTheme { DEFAULT, DAWN, NIGHT }

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
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
    /** 일정 한도 때문에 페이월을 자동으로 띄운 마지막 날(epoch day). 같은 날 반복 노출하지 않기 위함 */
    val lastPaywallPromptEpochDay: Long? = null,
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
