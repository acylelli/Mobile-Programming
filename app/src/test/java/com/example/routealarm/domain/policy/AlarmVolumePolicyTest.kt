package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.ScheduleSound
import com.example.routealarm.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmVolumePolicyTest {

    @Test
    fun streamIndex_scalesPercentToStreamSteps() {
        assertEquals(6, AlarmVolumePolicy.streamIndex(80, minIndex = 1, maxIndex = 7))
        assertEquals(7, AlarmVolumePolicy.streamIndex(100, minIndex = 1, maxIndex = 7))
    }

    @Test
    fun streamIndex_neverGoesSilentOrBelowDeviceMinimum() {
        assertEquals(1, AlarmVolumePolicy.streamIndex(10, minIndex = 0, maxIndex = 7))
        assertEquals(2, AlarmVolumePolicy.streamIndex(10, minIndex = 2, maxIndex = 7))
        // 범위를 벗어난 값은 10~100% 로 잘라서 쓴다.
        assertEquals(1, AlarmVolumePolicy.streamIndex(0, minIndex = 0, maxIndex = 7))
        assertEquals(7, AlarmVolumePolicy.streamIndex(250, minIndex = 0, maxIndex = 7))
    }

    @Test
    fun playerVolume_isFullWhenGradualIsOff() {
        assertEquals(1f, AlarmVolumePolicy.playerVolume(0, gradual = false))
    }

    @Test
    fun playerVolume_rampsFromQuietToFullOverThirtySeconds() {
        assertEquals(0.1f, AlarmVolumePolicy.playerVolume(0, gradual = true), 0.001f)
        assertEquals(0.55f, AlarmVolumePolicy.playerVolume(15_000, gradual = true), 0.001f)
        assertEquals(1f, AlarmVolumePolicy.playerVolume(30_000, gradual = true), 0.001f)
        assertEquals(1f, AlarmVolumePolicy.playerVolume(90_000, gradual = true), 0.001f)
    }

    @Test
    fun forSchedule_overridesSoundAndVolumeButKeepsGradual() {
        val prefs = UserPreferences(alarmSound = AlarmSound.DEFAULT_ALARM, alarmVolumePercent = 80, gradualVolume = true)
        assertEquals(prefs, AlarmVolumePolicy.forSchedule(prefs, null))

        val merged = AlarmVolumePolicy.forSchedule(prefs, ScheduleSound(AlarmSound.CUSTOM, "content://media/7", "Sapphire", volumePercent = 40))
        assertEquals(AlarmSound.CUSTOM, merged.alarmSound)
        assertEquals("content://media/7", merged.customSoundUri)
        assertEquals(40, merged.alarmVolumePercent)
        assertEquals(true, merged.gradualVolume)
    }

    @Test
    fun fromPreferences_copiesCurrentSettings() {
        val prefs = UserPreferences(alarmSound = AlarmSound.GENTLE, alarmVolumePercent = 55)
        assertEquals(ScheduleSound(AlarmSound.GENTLE, null, null, 55), AlarmVolumePolicy.fromPreferences(prefs))
    }

    @Test
    fun effectiveSound_fallsBackToDefaultWhenCustomUriIsMissing() {
        val custom = UserPreferences(alarmSound = AlarmSound.CUSTOM)
        assertEquals(AlarmSound.DEFAULT_ALARM, AlarmVolumePolicy.effectiveSound(custom))
        assertEquals(AlarmSound.CUSTOM, AlarmVolumePolicy.effectiveSound(custom.copy(customSoundUri = "content://media/1")))
        assertEquals(AlarmSound.SILENT, AlarmVolumePolicy.effectiveSound(UserPreferences(alarmSound = AlarmSound.SILENT)))
    }
}
