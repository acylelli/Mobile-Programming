package com.example.routealarm.alarm.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.policy.AlarmVolumePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 알람음 재생기. 알람 서비스와 설정 화면의 미리 듣기가 같이 쓴다.
 *
 * 울리는 동안만 알람 스트림 볼륨을 정한 크기로 맞추고, 멈추면 원래 볼륨으로 되돌린다.
 */
class AlarmSoundPlayer(private val context: Context) {

    private val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var rampJob: Job? = null
    private var originalStreamVolume: Int? = null

    val isPlaying: Boolean get() = player != null

    /** @return 재생을 시작했으면 true. 무음이거나 재생할 소리가 없으면 false */
    fun play(prefs: UserPreferences, scope: CoroutineScope): Boolean {
        stop()
        val sound = AlarmVolumePolicy.effectiveSound(prefs)
        if (sound == AlarmSound.SILENT) return false
        applyStreamVolume(prefs.alarmVolumePercent)
        // 고른 벨소리가 지워졌거나 읽을 수 없으면 기본 알람음으로 대신 울린다.
        val started = candidates(sound, prefs.customSoundUri).any { start(it) }
        if (!started) { restoreStreamVolume(); return false }
        startRamp(prefs.gradualVolume, scope)
        return true
    }

    /** 미리 듣기 중에 슬라이더를 움직이면 바로 그 크기로 바꾼다. */
    fun updateVolume(percent: Int) {
        if (player != null) applyStreamVolume(percent)
    }

    fun stop() {
        rampJob?.cancel()
        rampJob = null
        player?.runCatching { stop() }
        player?.release()
        player = null
        restoreStreamVolume()
    }

    private fun candidates(sound: AlarmSound, customUri: String?): List<Uri> = listOfNotNull(
        customUri?.takeIf { sound == AlarmSound.CUSTOM }?.toUri(),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).takeIf { sound == AlarmSound.GENTLE },
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
    )

    private fun start(uri: Uri): Boolean {
        val created = MediaPlayer()
        return runCatching {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            created.setDataSource(context, uri)
            created.isLooping = true
            created.prepare()
            created.start()
            player = created
        }.onFailure { created.release() }.isSuccess
    }

    private fun startRamp(gradual: Boolean, scope: CoroutineScope) {
        val startedAt = SystemClock.elapsedRealtime()
        player?.setVolume(AlarmVolumePolicy.playerVolume(0, gradual))
        if (!gradual) return
        rampJob = scope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val volume = AlarmVolumePolicy.playerVolume(elapsed, gradual = true)
                player?.runCatching { setVolume(volume) }
                if (elapsed >= AlarmVolumePolicy.RAMP_MILLIS) break
                delay(RAMP_TICK_MILLIS)
            }
        }
    }

    private fun MediaPlayer.setVolume(volume: Float) = setVolume(volume, volume)

    private fun applyStreamVolume(percent: Int) {
        val manager = audioManager ?: return
        runCatching {
            if (originalStreamVolume == null) originalStreamVolume = manager.getStreamVolume(AudioManager.STREAM_ALARM)
            val index = AlarmVolumePolicy.streamIndex(
                percent,
                minIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.getStreamMinVolume(AudioManager.STREAM_ALARM) else 0,
                maxIndex = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            )
            manager.setStreamVolume(AudioManager.STREAM_ALARM, index, 0)
        }
    }

    private fun restoreStreamVolume() {
        val original = originalStreamVolume ?: return
        originalStreamVolume = null
        audioManager?.runCatching { setStreamVolume(AudioManager.STREAM_ALARM, original, 0) }
    }

    private companion object {
        const val RAMP_TICK_MILLIS = 500L
    }
}
