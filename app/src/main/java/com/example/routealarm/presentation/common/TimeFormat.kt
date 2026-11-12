package com.example.routealarm.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.example.routealarm.R
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 화면 표시용 시간 포맷. 계산은 Instant, 표시만 기기 시간대로 변환한다. */
object TimeFormat {
    private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val M_D: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")
    private val M_D_HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
    private val M_D_E: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val M_D_EEEE: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
    private val YYYY_MM_DD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val WON: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.KOREA)

    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = HH_MM.format(instant.atZone(zone))
    fun time(time: LocalTime): String = HH_MM.format(time)
    fun date(date: LocalDate): String = M_D.format(date)
    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = M_D_HH_MM.format(instant.atZone(zone))

    /** 10월 23일 (금) */
    fun dateWithWeekday(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = M_D_E.format(instant.atZone(zone))
    fun dateWithWeekday(date: LocalDate): String = M_D_E.format(date)

    /** 10월 23일 금요일 */
    fun dateWithFullWeekday(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = M_D_EEEE.format(instant.atZone(zone))

    /** 2026.03.01 */
    fun dottedDate(date: LocalDate): String = YYYY_MM_DD.format(date)
    fun dottedDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = YYYY_MM_DD.format(instant.atZone(zone))

    /** 24,000 */
    fun amount(won: Int): String = WON.format(won)
}

@Composable
fun minutesText(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return when {
        m < 60 -> stringResource(R.string.common_minutes_format, m)
        m % 60 == 0 -> stringResource(R.string.common_hours_format, m / 60)
        else -> stringResource(R.string.common_hours_minutes_format, m / 60, m % 60)
    }
}

@Composable
fun wonText(won: Int): String = stringResource(R.string.common_won_format, TimeFormat.amount(won))

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

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** "평일", "매일", "화, 목", "10월 6일 한 번" */
@Composable
fun repeatText(repeatDays: Set<DayOfWeek>, oneTimeDate: LocalDate?): String = when {
    repeatDays.isEmpty() -> stringResource(R.string.repeat_once_format, oneTimeDate?.let(TimeFormat::date) ?: "")
    repeatDays.size == 7 -> stringResource(R.string.repeat_daily)
    repeatDays == WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    repeatDays == WEEKEND -> stringResource(R.string.repeat_weekend)
    else -> DayOfWeek.entries.filter { it in repeatDays }.map { dayShortName(it) }.joinToString(", ")
}

/** 알람 카드 머리말: "평일 반복", "화, 목 반복", "10월 23일 한 번" */
@Composable
fun repeatLabel(repeatDays: Set<DayOfWeek>, oneTimeDate: LocalDate?): String {
    val text = repeatText(repeatDays, oneTimeDate)
    return if (repeatDays.isEmpty()) text.trim() else stringResource(R.string.repeat_label_format, text)
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

/** 홈 제목: "내일 아침", "오늘 저녁", "금요일 아침" */
@Composable
fun dayPartTitle(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val zoned = instant.atZone(zone)
    val date = zoned.toLocalDate()
    val today = LocalDate.now(zone)
    val day = when {
        date == today -> stringResource(R.string.common_today)
        date == today.plusDays(1) -> stringResource(R.string.common_tomorrow)
        date.isAfter(today) && date.isBefore(today.plusDays(7)) ->
            date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.KOREAN)
        else -> TimeFormat.date(date)
    }
    val part = stringResource(
        when (zoned.hour) {
            in 4..11 -> R.string.day_part_morning
            in 12..17 -> R.string.day_part_afternoon
            in 18..21 -> R.string.day_part_evening
            else -> R.string.day_part_night
        },
    )
    return stringResource(R.string.home_title_format, day, part)
}

/** "방금 갱신", "10분 전 갱신", "3시간 전 갱신", "10월 22일 갱신" */
@Composable
fun relativeUpdateText(updatedAt: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val elapsed = Duration.between(updatedAt, now)
    return when {
        elapsed.toMinutes() < 1 -> stringResource(R.string.updated_just_now)
        elapsed.toMinutes() < 60 -> stringResource(R.string.updated_minutes_ago, elapsed.toMinutes().toInt())
        elapsed.toHours() < 24 -> stringResource(R.string.updated_hours_ago, elapsed.toHours().toInt())
        else -> stringResource(R.string.updated_on_date, TimeFormat.date(updatedAt.atZone(zone).toLocalDate()))
    }
}

/** 화면에 보이는 동안 주기적으로 갱신되는 현재 시각. "10분 전 갱신" 같은 상대 시간을 최신으로 유지한다. */
@Composable
fun rememberNow(intervalMillis: Long = 30_000L): State<Instant> = produceState(Instant.now(), intervalMillis) {
    while (true) {
        delay(intervalMillis)
        value = Instant.now()
    }
}
