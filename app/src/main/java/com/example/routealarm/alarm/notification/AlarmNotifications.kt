package com.example.routealarm.alarm.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.routealarm.MainActivity
import com.example.routealarm.R
import com.example.routealarm.alarm.service.AlarmRingingActivity
import com.example.routealarm.alarm.service.AlarmService
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.AlarmNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 알림 채널과 모든 Notification 생성을 한곳에서 관리한다.
 *
 * 채널을 셋으로 나누는 이유: 사용자가 "교통 상황 변경 알림"은 끄고 "기상 알람"은 켜 둘 수 있어야 한다.
 * Android 는 채널 단위로만 사용자 설정을 제공하므로 용도별 채널이 필요하다.
 */
@Singleton
class AlarmNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : AlarmNotifier {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channels = listOf(
            NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = context.getString(R.string.channel_alarm_description)
                    // 소리는 AlarmService 가 직접 재생하므로 채널 사운드는 끈다(이중 재생 방지).
                    setSound(null, alarmAttributes)
                    enableVibration(false)
                    setBypassDnd(true)
                },
            NotificationChannel(CHANNEL_DEPARTURE, context.getString(R.string.channel_departure), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = context.getString(R.string.channel_departure_description)
                    setSound(alarmSound, alarmAttributes)
                },
            NotificationChannel(CHANNEL_TRAFFIC, context.getString(R.string.channel_traffic), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_traffic_description) },
        )
        channels.forEach(manager::createNotificationChannel)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU

    /** 울리는 알람의 Foreground Service 알림. 전체 화면 인텐트로 잠금화면 위에 알람 화면을 띄운다. */
    fun buildRingingNotification(schedule: Schedule): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            schedule.id.toInt(),
            AlarmRingingActivity.createIntent(context, schedule.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val wakeUp = schedule.plan?.wakeUp?.let(::formatTime) ?: ""
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_ringing_title, wakeUp))
            .setContentText(context.getString(R.string.notification_ringing_text, schedule.destination.name))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, context.getString(R.string.action_snooze), AlarmService.pendingAction(context, schedule.id, AlarmService.ACTION_SNOOZE))
            .addAction(0, context.getString(R.string.action_dismiss), AlarmService.pendingAction(context, schedule.id, AlarmService.ACTION_DISMISS))
            .build()
    }

    fun notifyDepartureReminder(schedule: Schedule) {
        val plan = schedule.plan ?: return
        notify(
            NOTIFICATION_DEPARTURE_BASE + schedule.id.toInt(),
            CHANNEL_DEPARTURE,
            title = context.getString(R.string.notification_departure_reminder_title),
            text = context.getString(R.string.notification_departure_reminder_text, formatTime(plan.departure)),
            scheduleId = schedule.id,
        )
    }

    fun notifyDepartureNow(schedule: Schedule) {
        val plan = schedule.plan ?: return
        notify(
            NOTIFICATION_DEPARTURE_BASE + schedule.id.toInt(),
            CHANNEL_DEPARTURE,
            title = context.getString(R.string.notification_departure_now_title),
            text = context.getString(R.string.notification_departure_now_text, formatTime(plan.estimatedArrival), schedule.destination.name),
            scheduleId = schedule.id,
        )
    }

    override fun notifyAlarmAdjusted(schedule: Schedule, previousWakeUp: Instant, newWakeUp: Instant) {
        val delta = java.time.Duration.between(newWakeUp, previousWakeUp).toMinutes()
        val text = if (delta > 0) {
            context.getString(R.string.notification_adjusted_earlier, delta, formatTime(previousWakeUp), formatTime(newWakeUp))
        } else {
            context.getString(R.string.notification_adjusted_later, -delta, formatTime(previousWakeUp), formatTime(newWakeUp))
        }
        notify(
            NOTIFICATION_TRAFFIC_BASE + schedule.id.toInt(),
            CHANNEL_TRAFFIC,
            title = context.getString(R.string.notification_adjusted_title, schedule.title),
            text = text,
            scheduleId = schedule.id,
        )
    }

    override fun notifyAdjustmentSuggested(schedule: Schedule, deltaMinutes: Long, needsConfirmation: Boolean) {
        val magnitude = kotlin.math.abs(deltaMinutes)
        val text = when {
            needsConfirmation -> context.getString(R.string.notification_suggest_confirm, magnitude)
            deltaMinutes < 0 -> context.getString(R.string.notification_suggest_earlier, magnitude)
            else -> context.getString(R.string.notification_suggest_later, magnitude)
        }
        notify(
            NOTIFICATION_TRAFFIC_BASE + schedule.id.toInt(),
            CHANNEL_TRAFFIC,
            title = context.getString(R.string.notification_suggest_title, schedule.title),
            text = text,
            scheduleId = schedule.id,
        )
    }

    fun cancelDeparture(scheduleId: Long) = manager.cancel(NOTIFICATION_DEPARTURE_BASE + scheduleId.toInt())

    private fun notify(id: Int, channel: String, title: String, text: String, scheduleId: Long) {
        if (!hasPermission()) return
        val open = PendingIntent.getActivity(
            context, id, MainActivity.createIntent(context, scheduleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id, notification) }
    }

    private fun formatTime(instant: Instant): String = TIME_FORMAT.format(instant.atZone(timeProvider.zone()))

    companion object {
        const val CHANNEL_ALARM = "alarm"
        const val CHANNEL_DEPARTURE = "departure"
        const val CHANNEL_TRAFFIC = "traffic"
        const val NOTIFICATION_RINGING_ID = 1
        private const val NOTIFICATION_DEPARTURE_BASE = 10_000
        private const val NOTIFICATION_TRAFFIC_BASE = 20_000
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
