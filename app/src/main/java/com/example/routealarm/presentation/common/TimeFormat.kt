package com.example.routealarm.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.routealarm.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 화면 표시용 시간 포맷. 계산은 Instant, 표시만 기기 시간대로 변환한다. */
object TimeFormat {
    private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val M_D: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")
    private val M_D_HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = HH_MM.format(instant.atZone(zone))
    fun time(time: LocalTime): String = HH_MM.format(time)
    fun date(date: LocalDate): String = M_D.format(date)
    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = M_D_HH_MM.format(instant.atZone(zone))
}

@Composable
fun minutesText(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return if (m < 60) stringResource(R.string.common_minutes_format, m)
    else stringResource(R.string.common_hours_minutes_format, m / 60, m % 60)
}

@Composable
fun dayShortName(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.day_mon
        DayOfWeek.TUESDAY -> R.string.day_tue
        DayOfWeek.WEDNESDAY -> R.string.day_wed
        DayOfWeek.THURSDAY -> R.string.day_thu
        DayOfWeek.FRIDAY -> R.string.day_fri
        DayOfWeek.SATURDAY -> R.string.day_sat
        DayOfWeek.SUNDAY -> R.string.day_sun
    },
)

/** "평일", "매일", "월 수 금", "10월 6일 한 번" */
@Composable
fun repeatText(repeatDays: Set<DayOfWeek>, oneTimeDate: LocalDate?): String {
    val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return when {
        repeatDays.isEmpty() -> stringResource(R.string.repeat_once_format, oneTimeDate?.let(TimeFormat::date) ?: "")
        repeatDays.size == 7 -> stringResource(R.string.repeat_daily)
        repeatDays == weekdays -> stringResource(R.string.repeat_weekdays)
        repeatDays == weekend -> stringResource(R.string.repeat_weekend)
        else -> DayOfWeek.entries.filter { it in repeatDays }.map { dayShortName(it) }.joinToString(" ")
    }
}

/** 기상 시각이 오늘/내일/특정 날짜인지 */
@Composable
fun relativeDayText(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> stringResource(R.string.common_today)
        today.plusDays(1) -> stringResource(R.string.common_tomorrow)
        else -> TimeFormat.date(date)
    }
}
