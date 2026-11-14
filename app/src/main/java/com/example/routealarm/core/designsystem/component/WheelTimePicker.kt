package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalTime

/*
 * 가운데 묶음(오전 | 시 | 분)의 가로 배치. 아이폰 휠을 잰 값으로, 화면 너비와 관계없이 가운데 선 기준 위치가 같다.
 * 오전/오후는 가운데에서 왼쪽 60, 시는 오른쪽 끝이 +10 인 오른쪽 맞춤, 분은 오른쪽 68 이 글자 가운데다.
 * 누를 수 있는 영역은 -28 과 +32 에서 나뉘고 양옆 휠이 남은 폭을 갖는다.
 */
private val HourZoneWidth = 60.dp
private val ZoneShift = 4.dp
private val PeriodLabelWidth = 64.dp
private val HourLabelWidth = 38.dp
private val HourEndPadding = 22.dp
private val MinuteLabelWidth = 72.dp

private const val PERIOD_COUNT = 2
private const val AM_INDEX = 0
private const val PM_INDEX = 1

/**
 * 아이폰식 휠 시각 선택기. 오전/오후 · 시 · 분 세 휠을 돌려 시각을 고른다.
 *
 * - 값은 24시간제 [LocalTime] 으로 호이스팅된다. 휠에서 손을 떼면 멈출 시각이 바로 정해지므로 그때 한 번 [onTimeChange] 를 부른다
 *   (휠이 다 멈추기 전에 다음 화면으로 넘어가도 고른 시각이 반영된다).
 * - 시 휠이 11 과 12 사이를 지나면 오전/오후 휠이 따라 넘어간다. 분 휠은 시를 바꾸지 않는다.
 */
@Composable
fun WheelTimePicker(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = rememberWheelMetrics()
    val hourWheel = remember(metrics) { WheelState(time.hour, metrics.rowPx) }
    val minuteWheel = remember(metrics) { WheelState(time.minute, metrics.rowPx) }
    val periodWheel = remember(metrics) { WheelState(periodIndex(time.hour), metrics.rowPx, bounds = 0 until PERIOD_COUNT) }

    val currentTime by rememberUpdatedState(time)
    val currentOnTimeChange by rememberUpdatedState(onTimeChange)

    // 바깥에서 들어온 시각이 이 선택기가 알린 값의 메아리인지, 새로 정해진 값인지 가려낸다(시·분만 본다).
    val echo = remember { EchoFilter(minuteOfDay(time)) }

    // 휠들이 멈출(또는 멈춰 있는) 칸으로 고른 시각을 알린다.
    val report = remember(hourWheel, minuteWheel) {
        {
            val picked = WheelTimeMath.time(hourWheel.targetIndex, minuteWheel.targetIndex)
            if (minuteOfDay(picked) != minuteOfDay(currentTime)) {
                echo.reported(minuteOfDay(picked))
                currentOnTimeChange(picked)
            }
        }
    }

    // 바깥에서 시각이 바뀌면(수정할 일정을 불러옴 등) 휠을 그 시각으로 옮긴다.
    // 휠이 스스로 알린 시각이 되돌아온 경우에는 이미 그 칸을 향하고 있으므로 아무 일도 하지 않는다.
    LaunchedEffect(time, hourWheel, minuteWheel) {
        val isNew = echo.isNew(minuteOfDay(time))
        hourWheel.syncTo(time.hour, WheelTimeMath.HOURS_PER_DAY, force = isNew)
        minuteWheel.syncTo(time.minute, WheelTimeMath.MINUTES_PER_HOUR, force = isNew)
    }

    // 시 휠이 11 과 12 사이를 지나면 오전/오후 휠이 따라 넘어간다.
    // 오전/오후 휠을 사용자가 움직이는 중에 지나갔다면 그 움직임이 끝난 뒤에 맞춘다.
    LaunchedEffect(hourWheel, periodWheel) {
        snapshotFlow { WheelTimeMath.isPm(hourWheel.centeredIndex) to periodWheel.isUserActive }.collectLatest { (pm, busy) ->
            val target = if (pm) PM_INDEX else AM_INDEX
            if (!busy && (periodWheel.targetIndex != target || periodWheel.isBetweenRows)) {
                unlessPreempted { periodWheel.animateTo(target, byUser = false) }
            }
        }
    }

    // 손을 뗀 휠은 이미 값을 알렸다. 휠을 잡고 있는 채로 화면을 벗어나는 경우에만 그 순간 가운데에 있던 시각을 알린다.
    DisposableEffect(hourWheel, minuteWheel, report) {
        onDispose {
            if (hourWheel.isHeld || minuteWheel.isHeld) report()
        }
    }

    val textStyle = wheelTextStyle(WheelTextSize, metrics)
    val amLabel = stringResource(R.string.time_am)
    val pmLabel = stringResource(R.string.time_pm)
    val hourFormat = stringResource(R.string.time_hour_format)
    val minuteFormat = stringResource(R.string.common_minutes_format)

    WheelPickerFrame(metrics, modifier, contentPadding = PaddingValues(start = ZoneShift * metrics.scale)) {
        Wheel(
            state = periodWheel,
            label = { if (it == PM_INDEX) pmLabel else amLabel },
            valueText = { if (it == PM_INDEX) pmLabel else amLabel },
            valueCount = PERIOD_COUNT,
            description = stringResource(R.string.cd_time_period),
            style = textStyle,
            metrics = metrics,
            contentAlignment = Alignment.CenterEnd,
            labelWidth = PeriodLabelWidth * metrics.scale,
            onPick = {
                // 오전/오후를 직접 바꿨다: 보이는 숫자는 그대로 두고 시 휠을 반 바퀴(12칸) 옮긴다.
                val hourIndex = hourWheel.targetIndex
                hourWheel.jumpBy(WheelTimeMath.withPeriod(hourIndex, pm = periodWheel.targetIndex == PM_INDEX) - hourIndex)
                report()
            },
            modifier = Modifier.weight(1f),
        )
        Wheel(
            state = hourWheel,
            label = { WheelTimeMath.hourLabel(it).toString() },
            valueText = { (if (WheelTimeMath.isPm(it)) pmLabel else amLabel) + " " + hourFormat.format(WheelTimeMath.hourLabel(it)) },
            valueCount = WheelTimeMath.HOURS_PER_DAY,
            description = stringResource(R.string.cd_time_hour),
            style = textStyle,
            metrics = metrics,
            contentAlignment = Alignment.CenterEnd,
            contentPadding = PaddingValues(end = HourEndPadding * metrics.scale),
            labelWidth = HourLabelWidth * metrics.scale,
            labelTextAlign = TextAlign.End,
            onPick = report,
            onRest = report,
            modifier = Modifier.width(HourZoneWidth * metrics.scale),
        )
        Wheel(
            state = minuteWheel,
            label = { WheelTimeMath.minute(it).toString().padStart(2, '0') },
            valueText = { minuteFormat.format(WheelTimeMath.minute(it)) },
            valueCount = WheelTimeMath.MINUTES_PER_HOUR,
            description = stringResource(R.string.cd_time_minute),
            style = textStyle,
            metrics = metrics,
            contentAlignment = Alignment.CenterStart,
            labelWidth = MinuteLabelWidth * metrics.scale,
            onPick = report,
            onRest = report,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun minuteOfDay(time: LocalTime): Int = time.hour * WheelTimeMath.MINUTES_PER_HOUR + time.minute

private fun periodIndex(hourOfDay: Int): Int = if (hourOfDay >= WheelTimeMath.HOURS_PER_PERIOD) PM_INDEX else AM_INDEX

/**
 * 고리 휠을 값 [slot] 이 가운데로 오는 가장 가까운 칸으로 바로 옮긴다. 이미 그 값을 향하고 있으면 그대로 둔다.
 *
 * @param force 바깥에서 새로 정한 값이면 true: 관성으로 도는 휠도 멈추고 옮긴다. 손가락으로 잡고 있는 휠은 놓을 때 다시 알리므로 그대로 둔다.
 */
private suspend fun WheelState.syncTo(slot: Int, size: Int, force: Boolean) {
    val busy = if (force) isHeld else isUserActive
    if (!busy && (WheelRing.slot(targetIndex, size) != slot || isBetweenRows)) {
        unlessPreempted { snapTo(WheelRing.nearestIndex(targetIndex, slot, size)) }
    }
}

@Preview(showBackground = true, widthDp = 364)
@Composable
private fun WheelTimePickerPreview() {
    RouteAlarmTheme {
        var time by remember { mutableStateOf(LocalTime.of(3, 40)) }
        WheelTimePicker(time = time, onTimeChange = { time = it })
    }
}
