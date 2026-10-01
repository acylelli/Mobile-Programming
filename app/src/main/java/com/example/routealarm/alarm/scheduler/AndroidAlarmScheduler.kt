package com.example.routealarm.alarm.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.example.routealarm.MainActivity
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.repository.AlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager 기반 구현.
 *
 * - 기상 알람(WAKE_UP/SNOOZE)은 setAlarmClock 을 사용한다. Doze 모드에서도 정확히 울리고,
 *   시스템 상태바에 알람 아이콘이 표시되며, Android 12+ 에서는 SCHEDULE_EXACT_ALARM 없이도
 *   USE_EXACT_ALARM(알람 앱 전용 권한)으로 허용된다.
 * - 출발 알림(DEPARTURE_*)은 setExactAndAllowWhileIdle 을 사용하되, 정확한 알람 권한이 없으면
 *   setAndAllowWhileIdle(근사)로 대체하고 false 를 반환해 UI 가 권한 안내를 띄우게 한다.
 */
@Singleton
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(scheduleId: Long, type: AlarmType, triggerAt: Instant): Boolean {
        val operation = AlarmIntents.pendingBroadcast(
            context, scheduleId, type, PendingIntent.FLAG_UPDATE_CURRENT,
        ) ?: return false
        val triggerMillis = triggerAt.toEpochMilli()

        return when (type) {
            AlarmType.WAKE_UP, AlarmType.SNOOZE -> {
                val showIntent = PendingIntent.getActivity(
                    context,
                    AlarmIntents.requestCode(scheduleId, type),
                    MainActivity.createIntent(context, scheduleId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, showIntent), operation)
                true
            }

            AlarmType.DEPARTURE_REMINDER, AlarmType.DEPARTURE -> {
                if (canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
                    true
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, operation)
                    false
                }
            }
        }
    }

    override fun cancel(scheduleId: Long, types: Collection<AlarmType>) {
        types.forEach { type ->
            AlarmIntents.pendingBroadcast(context, scheduleId, type, PendingIntent.FLAG_NO_CREATE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }
}
