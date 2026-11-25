package com.example.routealarm.domain.model

/**
 * 컬러 테마(UI-F-002). 기본(오렌지)·새벽·나이트 3종이며 플랜과 관계없이 고를 수 있다.
 * 화면 모드(라이트/다크)는 따로 고르지 않는다. 기기 설정을 따르고, 나이트만 항상 어두운 화면이다.
 * 지하철 노선색·상태색 같은 정보 색은 테마와 관계없이 고정된다.
 */
enum class ColorTheme { DEFAULT, DAWN, NIGHT }

/** 알람음 종류. CUSTOM 은 휴대폰 벨소리 목록에서 고른 소리(customSoundUri)다. */
enum class AlarmSound { DEFAULT_ALARM, GENTLE, CUSTOM, SILENT }

/** DataStore 에 저장되는 작은 사용자 설정 */
data class UserPreferences(
    val defaultPreparationMinutes: Int = DEFAULT_PREPARATION_MINUTES,
    val defaultBufferMinutes: Int = DEFAULT_BUFFER_MINUTES,
    val autoAdjustment: Boolean = true,
    val trafficNotificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val alarmSound: AlarmSound = AlarmSound.DEFAULT_ALARM,
    /** 휴대폰 벨소리에서 고른 소리의 URI 와 이름. alarmSound 가 CUSTOM 일 때 쓴다. */
    val customSoundUri: String? = null,
    val customSoundTitle: String? = null,
    /** 알람이 울리는 크기(%). 휴대폰 알람 볼륨과 관계없이 이 크기로 울린다. */
    val alarmVolumePercent: Int = DEFAULT_ALARM_VOLUME_PERCENT,
    /** 작게 시작해 정한 크기까지 점점 커지게 울린다. */
    val gradualVolume: Boolean = false,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
    /** 일정 한도 때문에 페이월을 자동으로 띄운 마지막 날(epoch day). 같은 날 반복 노출하지 않기 위함 */
    val lastPaywallPromptEpochDay: Long? = null,
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        const val DEFAULT_PREPARATION_MINUTES = 30

        /** 준비 시간으로 고를 수 있는 범위(분)와 빠른 선택 값. 일정 만들기 4단계와 설정이 함께 쓴다. */
        val PREPARATION_MINUTES_RANGE = 5..120
        val PREPARATION_MINUTES_PRESETS = listOf(15, 30, 45, 60)
        const val DEFAULT_BUFFER_MINUTES = 10
        const val DEFAULT_SNOOZE_MINUTES = 5
        val SNOOZE_OPTIONS = listOf(5, 10, 15)
        const val DEFAULT_ALARM_VOLUME_PERCENT = 80
        val ALARM_VOLUME_RANGE = 10..100
    }
}

