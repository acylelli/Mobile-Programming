package com.example.routealarm.presentation.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColor
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
import com.example.routealarm.domain.model.ColorTheme
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.TrafficStatus
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.presentation.common.TimeFormat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
        fun preferencesRepository(): PreferencesRepository
        fun timeProvider(): TimeProvider
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val schedules = entryPoint.scheduleRepository().observeSchedules()
        val timeProvider = entryPoint.timeProvider()
        val widgetTheme = entryPoint.preferencesRepository().preferences.map { it.widgetTheme }.distinctUntilChanged()
        provideContent {
            // Glance 는 세션마다 provideGlance 를 한 번만 부르므로, 일정 변화는 구성 안에서 구독해야 바로 반영된다.
            val list by schedules.collectAsState(initial = null)
            val theme by widgetTheme.collectAsState(initial = null)
            val loaded = list ?: return@provideContent
            WidgetContent(context, NextAlarm.pick(loaded, timeProvider.now()), WidgetStyle.of(theme ?: ColorTheme.DEFAULT))
        }
    }
}

class NextAlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextAlarmWidget()
}

/**
 * 위젯 배경 3종. 앱 테마와 같은 이름이지만 홈 화면에서 눈에 띄도록 그라데이션 배경에 밝은 글자를 쓴다.
 * 배경이 늘 어두운 색이라 기기 다크 모드와 관계없이 같은 색으로 그린다.
 */
internal data class WidgetStyle(
    @DrawableRes val background: Int,
    val primary: GlanceColor,
    val secondary: GlanceColor,
    val accent: GlanceColor,
) {
    companion object {
        private fun fixed(color: Long) = ColorProvider(day = Color(color), night = Color(color))

        fun of(theme: ColorTheme): WidgetStyle = when (theme) {
            ColorTheme.DEFAULT -> WidgetStyle(R.drawable.widget_bg_default, fixed(0xFFFFFFFF), fixed(0xFFFFE2D4), fixed(0xFFFFF2B8))
            ColorTheme.DAWN -> WidgetStyle(R.drawable.widget_bg_dawn, fixed(0xFFF4EFE8), fixed(0xFFC9C2D6), fixed(0xFFF2B98B))
            ColorTheme.NIGHT -> WidgetStyle(R.drawable.widget_bg_night, fixed(0xFFF2F3F7), fixed(0xFFA0A6BA), fixed(0xFFC8F560))
        }
    }
}

@Composable
private fun WidgetContent(context: Context, schedule: Schedule?, style: WidgetStyle) {
    val primary = style.primary
    val secondary = style.secondary
    val accent = style.accent
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(style.background))
            .cornerRadius(22.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val plan = schedule?.plan
        if (schedule == null || plan == null) {
            Text(context.getString(R.string.widget_title), style = TextStyle(color = secondary, fontSize = 13.sp))
            Spacer(GlanceModifier.height(6.dp))
            Text(context.getString(R.string.widget_empty), style = TextStyle(color = primary, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            Text(context.getString(R.string.widget_empty_hint), style = TextStyle(color = accent, fontSize = 13.sp))
            return@Column
        }
        Text(
            context.getString(R.string.widget_label, TimeFormat.dateWithWeekday(plan.wakeUp), schedule.title),
            style = TextStyle(color = secondary, fontSize = 13.sp),
            maxLines = 1,
        )
        Text(TimeFormat.time(plan.wakeUp), style = TextStyle(color = primary, fontSize = 34.sp, fontWeight = FontWeight.Bold))
        Text(
            context.getString(
                R.string.widget_summary,
                schedule.destination.name,
                TimeFormat.time(plan.targetArrival),
                TimeFormat.time(plan.departure),
            ),
            style = TextStyle(color = primary, fontSize = 14.sp),
            maxLines = 1,
        )
        Text(
            trafficText(context, plan.trafficStatus),
            style = TextStyle(color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

private fun trafficText(context: Context, status: TrafficStatus): String = when (status) {
    TrafficStatus.Normal -> context.getString(R.string.traffic_normal)
    is TrafficStatus.MinorDelay -> context.getString(R.string.traffic_minor_delay, status.delayMinutes)
    is TrafficStatus.SevereDelay -> context.getString(R.string.traffic_severe_delay, status.delayMinutes)
}
