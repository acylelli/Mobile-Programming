package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* 가운데 묶음(시간 | 분)의 가로 배치. 두 휠이 폭을 반씩 나눠 갖고 글자는 가운데 쪽으로 붙는다. */
private val HalfGap = 14.dp
private val MinuteLabelWidth = 32.dp

private const val DEFAULT_STEP_MINUTES = 5
private const val MINUTES_PER_HOUR = 60
private const val ANNOUNCEMENT_MILLIS = 3_000L

/**
 * 아이폰 타이머식 휠 길이 선택기. 시간 · 분 두 휠을 돌려 걸리는 시간을 고른다.
 *
 * - 값은 분 단위 정수로 호이스팅된다. 휠에서 손을 떼면 멈출 값이 바로 정해지므로 그때 한 번 [onMinutesChange] 를 부른다.
 * - [range] 를 벗어난 조합(예: 최대 2시간인데 2시간 30분)은 가장 가까운 허용값으로 알리고, 휠도 멈춘 뒤 그 값으로 되돌아간다.
 *
 * @param range 고를 수 있는 범위(분). 양 끝은 [stepMinutes] 의 배수여야 한다.
 * @param stepMinutes 분 휠의 단위. 60 의 약수여야 한다.
 */
@Composable
fun WheelDurationPicker(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    stepMinutes: Int = DEFAULT_STEP_MINUTES,
) {
    require(stepMinutes in 1..MINUTES_PER_HOUR && MINUTES_PER_HOUR % stepMinutes == 0) { "stepMinutes must divide 60: $stepMinutes" }
    require(range.first >= 0 && range.first % stepMinutes == 0 && range.last % stepMinutes == 0) { "range must be on the step grid: $range" }

    val metrics = rememberWheelMetrics()
    val hourCount = WheelDurationMath.hourCount(range.last)
    val minuteSlots = WheelDurationMath.minuteSlots(stepMinutes)
    val hourWheel = remember(metrics, hourCount) {
        WheelState(WheelDurationMath.hourIndex(minutes.coerceIn(range.first, range.last)), metrics.rowPx, bounds = 0 until hourCount)
    }
    val minuteWheel = remember(metrics, stepMinutes) {
        WheelState(WheelDurationMath.minuteSlot(minutes.coerceIn(range.first, range.last), stepMinutes), metrics.rowPx)
    }

    val scope = rememberCoroutineScope()
    val currentMinutes by rememberUpdatedState(minutes)
    val currentOnMinutesChange by rememberUpdatedState(onMinutesChange)
    val hoursFormat = stringResource(R.string.common_hours_format)
    val minutesFormat = stringResource(R.string.common_minutes_format)
    val adjustedFormat = stringResource(R.string.cd_duration_adjusted)

    // 화면 낭독기에 한 번 읽어 줄 안내. 같은 안내를 다시 읽을 수 있도록 잠시 뒤에 비운다.
    var announcement by remember { mutableStateOf("") }
    LaunchedEffect(announcement) {
        if (announcement.isNotEmpty()) {
            delay(ANNOUNCEMENT_MILLIS)
            announcement = ""
        }
    }

    // 바깥에서 들어온 값이 이 선택기가 알린 값의 메아리인지, 새로 정해진 값(빠른 선택 버튼 등)인지 가려낸다.
    val echo = remember { EchoFilter(minutes) }

    // 휠들이 멈출(또는 멈춰 있는) 칸이 나타내는 값과, 그 값을 허용 범위 안으로 맞춘 값
    val picked = remember(hourWheel, minuteWheel, stepMinutes) {
        { WheelDurationMath.totalMinutes(hourWheel.targetIndex, minuteWheel.targetIndex, stepMinutes) }
    }
    val allowed = remember(picked, range) {
        { picked().coerceIn(range.first, range.last) }
    }
    val report = remember(allowed) {
        {
            val value = allowed()
            if (value != currentMinutes) {
                echo.reported(value)
                currentOnMinutesChange(value)
            }
        }
    }

    // 두 휠을 [total] 분에 맞춰 돌린다.
    // force: 바깥에서 새로 정한 값이면 관성으로 도는 휠도 멈추고 옮긴다. 손가락으로 잡고 있는 휠은 놓을 때 다시 알리므로 그대로 둔다.
    val moveTo: suspend (Int, Boolean) -> Unit = remember(hourWheel, minuteWheel, stepMinutes) {
        { total, force ->
            coroutineScope {
                val hourTarget = WheelDurationMath.hourIndex(total)
                val minuteSlot = WheelDurationMath.minuteSlot(total, stepMinutes)
                val hourBusy = if (force) hourWheel.isHeld else hourWheel.isUserActive
                val minuteBusy = if (force) minuteWheel.isHeld else minuteWheel.isUserActive
                if (!hourBusy && (hourWheel.targetIndex != hourTarget || hourWheel.isBetweenRows)) {
                    launch { unlessPreempted { hourWheel.animateTo(hourTarget, byUser = false) } }
                }
                if (!minuteBusy && (WheelRing.slot(minuteWheel.targetIndex, minuteSlots) != minuteSlot || minuteWheel.isBetweenRows)) {
                    val index = WheelRing.nearestIndex(minuteWheel.targetIndex, minuteSlot, minuteSlots)
                    launch { unlessPreempted { minuteWheel.animateTo(index, byUser = false) } }
                }
            }
        }
    }

    // 값을 골랐을 때: 고를 수 없는 조합이면 허용값으로 맞춰 알리고, 화면 낭독기 사용자에게도 바뀐 값을 말해 준다.
    // (다른 휠이 조용히 되돌아가므로, 초점이 놓인 휠만 듣는 사용자는 알려 주지 않으면 알 수 없다.)
    val pick = remember(picked, allowed, report) {
        {
            val value = allowed()
            if (picked() != value) {
                // 앱의 다른 곳과 같은 말로 읽는다: "2시간", "5분", "1시간 30분"
                val hours = value / MINUTES_PER_HOUR
                val rest = value % MINUTES_PER_HOUR
                val text = listOfNotNull(
                    hoursFormat.format(hours).takeIf { hours > 0 },
                    minutesFormat.format(rest).takeIf { rest > 0 || hours == 0 },
                ).joinToString(" ")
                announcement = adjustedFormat.format(text)
            }
            report()
        }
    }

    // 휠이 멈췄을 때: 고를 수 없는 조합에 멈춰 있으면 가장 가까운 허용값으로 되돌린다.
    val rest = remember(picked, allowed, moveTo, report) {
        {
            val value = allowed()
            if (picked() != value) scope.launch { moveTo(value, false) }
            report()
        }
    }

    // 바깥에서 값이 바뀌면(빠른 선택 버튼 등) 휠을 그 값으로 돌린다.
    // 휠이 스스로 알린 값이 되돌아온 경우에는 이미 그 칸을 향하고 있으므로 아무 일도 하지 않는다.
    LaunchedEffect(minutes, moveTo, range) {
        moveTo(minutes.coerceIn(range.first, range.last), echo.isNew(minutes))
    }

    // 손을 뗀 휠은 이미 값을 알렸다. 휠을 잡고 있는 채로 화면을 벗어나는 경우에만 그 순간 가운데에 있던 값을 알린다.
    DisposableEffect(hourWheel, minuteWheel, report) {
        onDispose {
            if (hourWheel.isHeld || minuteWheel.isHeld) report()
        }
    }

    val digitStyle = wheelTextStyle(WheelTextSize, metrics)
    val unitStyle = wheelTextStyle(WheelUnitSize, metrics, FontWeight.Medium)

    WheelPickerFrame(metrics, modifier, announcement = announcement) {
        Wheel(
            state = hourWheel,
            label = { it.toString() },
            valueText = { hoursFormat.format(it) },
            valueCount = hourCount,
            description = stringResource(R.string.cd_duration_hour),
            style = digitStyle,
            metrics = metrics,
            unit = stringResource(R.string.time_unit_hour),
            unitStyle = unitStyle,
            contentAlignment = Alignment.CenterEnd,
            contentPadding = PaddingValues(end = HalfGap * metrics.scale),
            onPick = pick,
            onRest = rest,
            modifier = Modifier.weight(1f),
        )
        Wheel(
            state = minuteWheel,
            label = { WheelDurationMath.minuteLabel(it, stepMinutes).toString() },
            valueText = { minutesFormat.format(WheelDurationMath.minuteLabel(it, stepMinutes)) },
            valueCount = minuteSlots,
            description = stringResource(R.string.cd_duration_minute),
            style = digitStyle,
            metrics = metrics,
            labelWidth = MinuteLabelWidth * metrics.scale,
            labelTextAlign = TextAlign.End,
            unit = stringResource(R.string.time_unit_minute),
            unitStyle = unitStyle,
            contentAlignment = Alignment.CenterStart,
            contentPadding = PaddingValues(start = HalfGap * metrics.scale),
            onPick = pick,
            onRest = rest,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true, widthDp = 364)
@Composable
private fun WheelDurationPickerPreview() {
    RouteAlarmTheme {
        var minutes by remember { mutableIntStateOf(30) }
        WheelDurationPicker(minutes = minutes, onMinutesChange = { minutes = it }, range = 5..120)
    }
}
