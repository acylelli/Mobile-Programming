package com.example.routealarm.alarm.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.routealarm.alarm.receiver.AlarmReceiver
import com.example.routealarm.domain.model.AlarmType

/**
 * AlarmManager 와 BroadcastReceiver 가 공유하는 Intent 규약.
 * requestCode = scheduleId * 10 + type.offset 으로 일정별·종류별 PendingIntent 를 구분한다.
 */
object AlarmIntents {
    const val ACTION_ALARM = "com.example.routealarm.action.ALARM"

    /** 알람 변경 추천 알림의 [변경하기] 버튼 */
    const val ACTION_APPLY_ADJUSTMENT = "com.example.routealarm.action.APPLY_ADJUSTMENT"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_ALARM_TYPE = "alarm_type"
    private const val REQUEST_CODE_STRIDE = 10

    fun requestCode(scheduleId: Long, type: AlarmType): Int =
        (scheduleId * REQUEST_CODE_STRIDE + type.requestCodeOffset).toInt()

    fun broadcast(context: Context, scheduleId: Long, type: AlarmType): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            .putExtra(EXTRA_ALARM_TYPE, type.name)

    /** 추천된 변경안을 앱을 열지 않고 바로 적용하는 PendingIntent. 액션이 달라 알람 PendingIntent 와 겹치지 않는다. */
    fun pendingApplyAdjustment(context: Context, scheduleId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_APPLY_ADJUSTMENT)
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun pendingBroadcast(context: Context, scheduleId: Long, type: AlarmType, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(scheduleId, type),
            broadcast(context, scheduleId, type),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
}
