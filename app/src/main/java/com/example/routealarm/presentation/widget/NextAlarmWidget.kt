package com.example.routealarm.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.routealarm.MainActivity
import com.example.routealarm.R
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.TrafficStatus
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.presentation.common.TimeFormat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 홈 화면 위젯: 다음 기상 알람 시각과 출발·도착, 교통 상태를 보여 준다. 누르면 앱이 열린다.
 * 위젯이 떠 있는 동안 일정 변화를 구독해 바로 다시 그리고, 세션이 끝난 뒤의 변화는 [WidgetUpdater] 가,
 * 시간 흐름(지난 알람 넘기기)은 30분마다의 갱신이 반영한다.
 */
class NextAlarmWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun scheduleRepository(): ScheduleRepository
        fun timeProvider(): TimeProvider
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val schedules = entryPoint.scheduleRepository().observeSchedules()
        val timeProvider = entryPoint.timeProvider()
        provideContent {
            // Glance 는 세션마다 provideGlance 를 한 번만 부르므로, 일정 변화는 구성 안에서 구독해야 바로 반영된다.
            val list by schedules.collectAsState(initial = null)
            val loaded = list ?: return@provideContent
            WidgetContent(context, NextAlarm.pick(loaded, timeProvider.now()))
        }
    }
}

class NextAlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextAlarmWidget()
}

private val Background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1C1E))
private val Primary = ColorProvider(day = Color(0xFF111111), night = Color(0xFFF5F5F5))
private val Secondary = ColorProvider(day = Color(0xFF6B6B6B), night = Color(0xFFAAAAAA))
private val Accent = ColorProvider(day = Color(0xFFFF5A1F), night = Color(0xFFFF7A45))

@Composable
private fun WidgetContent(context: Context, schedule: Schedule?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Background)
            .cornerRadius(22.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val plan = schedule?.plan
        if (schedule == null || plan == null) {
            Text(context.getString(R.string.widget_title), style = TextStyle(color = Secondary, fontSize = 13.sp))
            Spacer(GlanceModifier.height(6.dp))
            Text(context.getString(R.string.widget_empty), style = TextStyle(color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            Text(context.getString(R.string.widget_empty_hint), style = TextStyle(color = Accent, fontSize = 13.sp))
            return@Column
        }
        Text(
            context.getString(R.string.widget_label, TimeFormat.dateWithWeekday(plan.wakeUp), schedule.title),
            style = TextStyle(color = Secondary, fontSize = 13.sp),
            maxLines = 1,
        )
        Text(TimeFormat.time(plan.wakeUp), style = TextStyle(color = Primary, fontSize = 40.sp, fontWeight = FontWeight.Bold))
        Text(
            context.getString(
                R.string.widget_summary,
                schedule.destination.name,
                TimeFormat.time(plan.targetArrival),
                TimeFormat.time(plan.departure),
            ),
            style = TextStyle(color = Primary, fontSize = 14.sp),
            maxLines = 1,
        )
        Text(
            trafficText(context, plan.trafficStatus),
            style = TextStyle(color = if (plan.trafficStatus is TrafficStatus.Normal) Secondary else Accent, fontSize = 13.sp),
            maxLines = 1,
        )
    }
}

private fun trafficText(context: Context, status: TrafficStatus): String = when (status) {
    TrafficStatus.Normal -> context.getString(R.string.traffic_normal)
    is TrafficStatus.MinorDelay -> context.getString(R.string.traffic_minor_delay, status.delayMinutes)
    is TrafficStatus.SevereDelay -> context.getString(R.string.traffic_severe_delay, status.delayMinutes)
}
