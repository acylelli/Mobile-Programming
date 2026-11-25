package com.example.routealarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.routealarm.alarm.notification.AlarmNotifications
import com.example.routealarm.alarm.scheduler.AlarmIntents
import com.example.routealarm.alarm.service.AlarmService
import com.example.routealarm.core.common.ApplicationScope
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.usecase.ApplyPendingAdjustmentUseCase
import com.example.routealarm.domain.usecase.DepartureBriefingUseCase
import com.example.routealarm.domain.usecase.HandleAlarmEventUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * AlarmManager 가 발사한 알람의 진입점.
 * 기상 알람은 Foreground Service 로 넘겨 소리/진동/알람 화면을 담당하게 하고,
 * 출발 알림은 일반 Notification 으로 끝낸다. DB 작업은 goAsync 로 리시버 수명을 잠시 연장해 처리한다.
 *
 * 출발 알림 직전에는 경로를 한 번 더 조회해 실시간 도착 정보("다산역 8호선 · 약 8분 후 도착")를 담는다.
 * 리시버가 오래 붙잡히지 않도록 [BRIEFING_TIMEOUT_MILLIS] 안에 끝나지 않으면 마지막 계산값으로 알림을 보낸다.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var handleAlarmEvent: HandleAlarmEventUseCase
    @Inject lateinit var applyPendingAdjustment: ApplyPendingAdjustmentUseCase
    @Inject lateinit var departureBriefing: DepartureBriefingUseCase
    @Inject lateinit var notifications: AlarmNotifications
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmIntents.ACTION_APPLY_ADJUSTMENT) {
            applyAdjustment(intent.getLongExtra(AlarmIntents.EXTRA_SCHEDULE_ID, -1))
            return
        }
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
                        // 출발 알림(DEPARTURE)은 onFired 에서 다음 회차로 넘어가므로, 그 전에 이번 회차 경로를 갱신한다.
                        val briefed = runCatching {
                            withTimeoutOrNull(BRIEFING_TIMEOUT_MILLIS) { departureBriefing(scheduleId) }
                        }.getOrNull()
                        val schedule = handleAlarmEvent.onFired(scheduleId, type)
                        val forNotification = briefed ?: schedule
                        if (forNotification != null) {
                            if (type == AlarmType.DEPARTURE_REMINDER) notifications.notifyDepartureReminder(forNotification)
                            else notifications.notifyDepartureNow(forNotification)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        /** goAsync 는 약 10초 안에 끝내야 한다. 경로 조회가 늦으면 기다리지 않고 알림을 보낸다. */
        const val BRIEFING_TIMEOUT_MILLIS = 7_000L
    }

    /** 알람 변경 추천 알림에서 [변경하기]를 누른 경우: 변경안을 적용하고 알림을 지운다. */
    private fun applyAdjustment(scheduleId: Long) {
        if (scheduleId < 0) return
        val pending = goAsync()
        scope.launch {
            try {
                applyPendingAdjustment(scheduleId)
                notifications.cancelTraffic(scheduleId)
            } finally {
                pending.finish()
            }
        }
    }
}
