package com.example.routealarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.routealarm.alarm.notification.AlarmNotifications
import com.example.routealarm.alarm.scheduler.AlarmIntents
import com.example.routealarm.alarm.service.AlarmService
import com.example.routealarm.core.common.ApplicationScope
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.usecase.HandleAlarmEventUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AlarmManager 가 발사한 알람의 진입점.
 * 기상 알람은 Foreground Service 로 넘겨 소리/진동/알람 화면을 담당하게 하고,
 * 출발 알림은 일반 Notification 으로 끝낸다. DB 작업은 goAsync 로 리시버 수명을 잠시 연장해 처리한다.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var handleAlarmEvent: HandleAlarmEventUseCase
    @Inject lateinit var notifications: AlarmNotifications
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntents.ACTION_ALARM) return
        val scheduleId = intent.getLongExtra(AlarmIntents.EXTRA_SCHEDULE_ID, -1)
        val type = intent.getStringExtra(AlarmIntents.EXTRA_ALARM_TYPE)
            ?.let { name -> AlarmType.entries.firstOrNull { it.name == name } } ?: return
        if (scheduleId < 0) return

        when (type) {
            AlarmType.WAKE_UP, AlarmType.SNOOZE -> {
                // 서비스 시작은 리시버 안에서 즉시 해야 백그라운드 시작 제한에 걸리지 않는다.
                AlarmService.start(context, scheduleId)
                val pending = goAsync()
                scope.launch {
                    try { handleAlarmEvent.onFired(scheduleId, type) } finally { pending.finish() }
                }
            }

            AlarmType.DEPARTURE_REMINDER, AlarmType.DEPARTURE -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        val schedule = handleAlarmEvent.onFired(scheduleId, type)
                        if (schedule != null) {
                            if (type == AlarmType.DEPARTURE_REMINDER) notifications.notifyDepartureReminder(schedule)
                            else notifications.notifyDepartureNow(schedule)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
