package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.UserPreferences
import kotlin.math.roundToInt

/**
 * 알람 소리 크기 규칙.
 *
 * 휴대폰 알람 볼륨과 관계없이 사용자가 정한 크기로 울리도록, 울리는 동안만 알람 스트림 볼륨을
 * 정한 비율로 맞추고 끝나면 되돌린다. "점점 크게"를 켜면 플레이어 볼륨을 작게 시작해 30초에 걸쳐 키운다.
 */
object AlarmVolumePolicy {
    const val RAMP_MILLIS = 30_000L
    const val RAMP_START_FRACTION = 0.1f

    /** 정한 비율(%)을 알람 스트림 볼륨 단계로 바꾼다. 0 으로 내려가 무음이 되지 않게 최소 단계를 지킨다. */
    fun streamIndex(percent: Int, minIndex: Int, maxIndex: Int): Int {
        val clamped = percent.coerceIn(UserPreferences.ALARM_VOLUME_RANGE)
        return (clamped / 100f * maxIndex).roundToInt().coerceIn(maxOf(minIndex, 1), maxIndex)
    }

    /** 울리기 시작한 지 elapsedMillis 지났을 때 플레이어 볼륨(0~1). 점점 크게가 꺼져 있으면 항상 1 이다. */
    fun playerVolume(elapsedMillis: Long, gradual: Boolean): Float {
        if (!gradual) return 1f
        val progress = (elapsedMillis.toFloat() / RAMP_MILLIS).coerceIn(0f, 1f)
        return RAMP_START_FRACTION + (1f - RAMP_START_FRACTION) * progress
    }

    /** 실제로 쓸 알람음. 벨소리를 골랐는데 URI 가 없으면 기본 알람음으로 울린다. */
    fun effectiveSound(prefs: UserPreferences): AlarmSound =
        if (prefs.alarmSound == AlarmSound.CUSTOM && prefs.customSoundUri.isNullOrBlank()) AlarmSound.DEFAULT_ALARM else prefs.alarmSound
}
