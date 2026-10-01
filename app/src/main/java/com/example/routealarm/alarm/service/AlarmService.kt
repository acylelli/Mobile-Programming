package com.example.routealarm.alarm.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.routealarm.alarm.notification.AlarmNotifications
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.usecase.HandleAlarmEventUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 알람이 울리는 동안 실행되는 Foreground Service.
 *
 * Activity 가 아니라 Service 가 소리/진동을 담당하는 이유: 사용자가 알람 화면을 닫거나 화면이 꺼져도
 * 알람은 계속 울려야 하고, Android 는 백그라운드 재생을 Foreground Service 로만 보장한다.
 * 알람 화면(AlarmRingingActivity)은 이 서비스의 상태를 보여주고 끄기/다시 울림 명령만 보낸다.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var notifications: AlarmNotifications
    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject lateinit var preferencesRepository: PreferencesRepository
    @Inject lateinit var handleAlarmEvent: HandleAlarmEventUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var scheduleId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_SCHEDULE_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_SNOOZE -> { snooze(id); return START_NOT_STICKY }
            ACTION_DISMISS -> { dismiss(id); return START_NOT_STICKY }
        }
        if (id < 0) { stopSelf(); return START_NOT_STICKY }
        scheduleId = id
        scope.launch { startRinging(id) }
        return START_NOT_STICKY
    }

    private suspend fun startRinging(id: Long) {
        val schedule = scheduleRepository.getSchedule(id)
        if (schedule == null) { stopSelf(); return }
        val prefs = preferencesRepository.current()

        val notification = notifications.buildRingingNotification(schedule)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, AlarmNotifications.NOTIFICATION_RINGING_ID, notification, type)
        ringingScheduleId.value = id

        if (prefs.alarmSound != AlarmSound.SILENT) playSound(prefs.alarmSound)
        if (prefs.vibrationEnabled) vibrate()

        // 안전장치: 아무 조작이 없어도 영원히 울리지 않는다.
        scope.launch {
            kotlinx.coroutines.delay(AUTO_SILENCE_MILLIS)
            if (ringingScheduleId.value == id) snooze(id)
        }
    }

    private fun playSound(sound: AlarmSound) {
        val uri = when (sound) {
            AlarmSound.GENTLE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getSystemService(this, VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(this, Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
    }

    private fun snooze(id: Long) {
        scope.launch {
            val minutes = preferencesRepository.current().snoozeMinutes
            handleAlarmEvent.onSnoozed(id, minutes)
            stopRinging()
        }
    }

    private fun dismiss(id: Long) {
        scope.launch {
            handleAlarmEvent.onDismissed(id)
            stopRinging()
        }
    }

    private fun stopRinging() {
        ringingScheduleId.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        ringingScheduleId.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SNOOZE = "com.example.routealarm.action.SNOOZE"
        const val ACTION_DISMISS = "com.example.routealarm.action.DISMISS"
        private const val EXTRA_SCHEDULE_ID = "schedule_id"
        private const val AUTO_SILENCE_MILLIS = 5L * 60 * 1000
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 600)

        /** 현재 울리고 있는 일정 ID. 알람 화면이 구독해 서비스가 끝나면 스스로 닫힌다. */
        private val ringingScheduleId = MutableStateFlow<Long?>(null)
        val ringing: StateFlow<Long?> get() = ringingScheduleId

        fun start(context: Context, scheduleId: Long) {
            val intent = Intent(context, AlarmService::class.java).putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun snooze(context: Context, scheduleId: Long) = context.startService(actionIntent(context, scheduleId, ACTION_SNOOZE))
        fun dismiss(context: Context, scheduleId: Long) = context.startService(actionIntent(context, scheduleId, ACTION_DISMISS))

        private fun actionIntent(context: Context, scheduleId: Long, action: String) =
            Intent(context, AlarmService::class.java).setAction(action).putExtra(EXTRA_SCHEDULE_ID, scheduleId)

        fun pendingAction(context: Context, scheduleId: Long, action: String): PendingIntent =
            PendingIntent.getService(
                context, (scheduleId * 10 + action.hashCode().mod(10)).toInt(), actionIntent(context, scheduleId, action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
