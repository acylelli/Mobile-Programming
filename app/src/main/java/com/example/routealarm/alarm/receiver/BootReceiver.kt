package com.example.routealarm.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.routealarm.core.common.ApplicationScope
import com.example.routealarm.domain.usecase.RestoreAlarmsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 재부팅, 앱 업데이트, 시간대/시스템 시간 변경 시 AlarmManager 등록을 복구한다.
 *
 * AlarmManager 등록은 재부팅하면 사라지고, 시간대가 바뀌면 "09:00 도착"의 절대 시각(Instant)이 달라진다.
 * 네트워크 없이도 Room 의 마지막 계산값으로 즉시 복구하고, 실시간 갱신은 WorkManager 에 맡긴다.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var restoreAlarms: RestoreAlarmsUseCase
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try { restoreAlarms() } finally { pending.finish() }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
