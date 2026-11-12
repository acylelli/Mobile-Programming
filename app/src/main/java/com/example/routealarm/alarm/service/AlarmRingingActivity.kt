package com.example.routealarm.alarm.service

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.StripLabel
import com.example.routealarm.core.designsystem.component.TimelineStrip
import com.example.routealarm.core.designsystem.component.TrafficStatusChip
import com.example.routealarm.core.designsystem.theme.AppType
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.presentation.common.RouteNodeKind
import com.example.routealarm.presentation.common.RouteTimeline
import com.example.routealarm.presentation.common.RouteTimelineBuilder
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.rememberNow
import com.example.routealarm.presentation.common.stationShortName
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * 알람이 울릴 때 잠금화면 위에 뜨는 화면. 소리/진동은 AlarmService 가 담당하고 여기서는 끄기/다시 울림만 보낸다.
 * 서비스가 끝나면(알림 액션으로 껐을 때도) 자동으로 닫힌다.
 */
@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {

    private val viewModel: AlarmRingingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1)
        if (scheduleId < 0) { finish(); return }
        viewModel.load(scheduleId)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
            var hasRung by remember { mutableStateOf(false) }
            LaunchedEffect(ringing) {
                when {
                    ringing != null -> hasRung = true
                    hasRung -> finish()
                    else -> {
                        // 화면이 서비스보다 먼저 뜬 경우 잠깐 기다린다. 끝내 울리지 않으면 닫는다.
                        delay(SERVICE_START_GRACE_MILLIS)
                        if (AlarmService.ringing.value == null) finish()
                    }
                }
            }

            RouteAlarmTheme(colorTheme = state.colorTheme) {
                // 배경이 강조색 한 면이라 상태바 아이콘은 그 위 글자색(onAccent)의 밝기를 따른다.
                val lightIcons = RouteAlarmTheme.colors.onAccent.luminance() > 0.5f
                DisposableEffect(lightIcons) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (lightIcons) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                AlarmRingingScreen(
                    schedule = state.schedule,
                    onSnooze = { minutes -> AlarmService.snooze(this, scheduleId, minutes) },
                    onDismiss = { AlarmService.dismiss(this, scheduleId) },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        private const val SERVICE_START_GRACE_MILLIS = 3_000L

        fun createIntent(context: Context, scheduleId: Long): Intent =
            Intent(context, AlarmRingingActivity::class.java)
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/** 화면에서 고를 수 있는 다시 울림 간격(UI-D-003 ④) */
private val SNOOZE_CHOICES = listOf(5, 10)

/**
 * 기상 알람 — UI-D-003.
 * 강조색 전체 화면에 현재 시각을 크게, 오늘의 경로를 요약해서 보여준다. 광고나 구독 안내는 넣지 않는다(SFR-015).
 */
@Composable
fun AlarmRingingScreen(
    schedule: Schedule?,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
    now: Instant = rememberNow(intervalMillis = 1_000L).value,
) {
    val colors = RouteAlarmTheme.colors
    val plan = schedule?.plan
    // 강조색이 어두우면(모노 테마) 밝은 막을, 밝으면 어두운 막을 덮어 반투명 버튼을 만든다.
    val veil = if (colors.accent.luminance() < 0.2f) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.22f)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.accent)
            .systemBarsPadding()
            .padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 위쪽 정보는 화면이 작으면 스크롤되고, 끄기·다시 울림 버튼은 항상 아래에 고정된다.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(52.dp))
            Text(
                stringResource(R.string.ring_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onAccent,
                modifier = Modifier.semantics { heading() },
            )
            // 기상 시각(①): 잠결에도 바로 읽히도록 화면에서 가장 크게
            Text(TimeFormat.time(now), style = AppType.RingTime, color = colors.onAccent, maxLines = 1, softWrap = false)
            Text(
                if (schedule != null) {
                    stringResource(
                        R.string.ring_subtitle_format,
                        TimeFormat.dateWithFullWeekday(now),
                        schedule.destination.name,
                        TimeFormat.time(schedule.targetArrivalTime),
                    )
                } else {
                    TimeFormat.dateWithFullWeekday(now)
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onAccent,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (schedule != null && plan != null) {
                Spacer(Modifier.height(28.dp))
                val originName = if (schedule.useCurrentLocation) stringResource(R.string.create_current_location_name) else schedule.origin.name
                val timeline = remember(plan, originName) { RouteTimelineBuilder.build(plan, originName, schedule.destination.name) }
                TodayRouteCard(timeline, plan.trafficStatus)
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(20.dp))
        // 끄기(③): 지름 112dp 의 큰 원형 버튼
        Box(
            Modifier
                .size(112.dp)
                .shadow(elevation = 14.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(colors.background)
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.ring_dismiss),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp),
                color = colors.accentText,
            )
        }
        Spacer(Modifier.height(28.dp))
        // 다시 울림(④): 반투명 배경의 굵은 글자 버튼
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SNOOZE_CHOICES.forEach { minutes ->
                Box(
                    Modifier
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(veil)
                        .clickable(role = Role.Button, onClick = { onSnooze(minutes) })
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.ring_snooze_format, minutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onAccent,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** 오늘의 경로(②): 출발, 환승, 도착 시각과 교통 상태를 요약 */
@Composable
private fun TodayRouteCard(timeline: RouteTimeline, trafficStatus: com.example.routealarm.domain.model.TrafficStatus) {
    val colors = RouteAlarmTheme.colors
    val departure = timeline.nodes.firstOrNull { it.kind == RouteNodeKind.DEPARTURE }
    val arrival = timeline.nodes.lastOrNull { it.kind == RouteNodeKind.ARRIVAL }
    val middle = timeline.nodes.firstOrNull { it.kind == RouteNodeKind.TRANSFER }
        ?: timeline.nodes.firstOrNull { it.kind == RouteNodeKind.BOARD }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background, RoundedCornerShape(24.dp))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ring_route_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TrafficStatusChip(trafficStatus)
        }
        Spacer(Modifier.height(14.dp))
        TimelineStrip(timeline)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (departure != null) {
                    StripLabel(TimeFormat.time(departure.time), stringResource(R.string.route_node_departure), Alignment.Start)
                }
            }
            Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                if (middle != null) {
                    val caption = if (middle.kind == RouteNodeKind.TRANSFER) {
                        stringResource(R.string.route_node_transfer_format, stationShortName(middle.placeName))
                    } else {
                        stringResource(R.string.ring_board_format, middle.placeName)
                    }
                    StripLabel(TimeFormat.time(middle.time), caption, Alignment.CenterHorizontally)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (arrival != null) {
                    StripLabel(TimeFormat.time(arrival.time), stringResource(R.string.ring_arrival), Alignment.End)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 820)
@Composable
private fun AlarmRingingPreview() {
    RouteAlarmTheme {
        AlarmRingingScreen(schedule = null, onSnooze = {}, onDismiss = {}, now = Instant.parse("2026-10-22T22:15:00Z"))
    }
}
