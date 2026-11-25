package com.example.routealarm.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.repository.CalendarEvent
import com.example.routealarm.domain.repository.CalendarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기 캘린더(CalendarContract)를 읽는다. Google·삼성 캘린더 일정은 기기 캘린더로 동기화되어 있어서
 * Google 로그인이나 API 키 없이 읽을 수 있다. 쓰기 권한은 요청하지 않는다.
 */
@Singleton
class DeviceCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CalendarRepository {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun upcomingEvents(days: Long): AppResult<List<CalendarEvent>> = withContext(ioDispatcher) {
        if (!hasPermission()) return@withContext AppResult.Failure(AppError.CalendarPermissionDenied)
        val now = timeProvider.now()
        val end = now.plus(Duration.ofDays(days))
        // Instances 는 반복 일정을 실제 날짜별로 펼쳐 준다. 같은 일정의 여러 회차는 첫 회차만 남긴다.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now.toEpochMilli())
            ContentUris.appendId(it, end.toEpochMilli())
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.ALL_DAY,
        )
        try {
            val events = linkedMapOf<Long, CalendarEvent>()
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(5) == 1) continue // 하루 종일 일정은 도착 시각이 없다
                    val id = c.getLong(0)
                    if (id in events) continue
                    val begin = Instant.ofEpochMilli(c.getLong(2))
                    events[id] = CalendarEvent(
                        id = id,
                        title = c.getString(1)?.trim().orEmpty(),
                        begin = begin,
                        location = c.getString(3)?.trim()?.takeIf { it.isNotEmpty() },
                        weeklyDays = weeklyDays(c.getString(4), begin),
                    )
                }
            }
            AppResult.Success(events.values.toList())
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.CalendarPermissionDenied)
        }
    }

    /** RRULE 이 매주 반복이면 반복 요일(BYDAY, 없으면 첫 회차 요일). 매주 반복이 아니면 빈 집합 */
    private fun weeklyDays(rrule: String?, begin: Instant): Set<DayOfWeek> = when {
        rrule == null || !rrule.contains("FREQ=WEEKLY") -> emptySet()
        else -> parseWeeklyDays(rrule) ?: setOf(begin.atZone(timeProvider.zone()).dayOfWeek)
    }

    companion object {
        private val BYDAY = mapOf(
            "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
        )

        /** "FREQ=WEEKLY;BYDAY=MO,WE" → {월, 수}. 매주 반복이 아니거나 BYDAY 가 없으면 null */
        fun parseWeeklyDays(rrule: String?): Set<DayOfWeek>? {
            if (rrule == null || !rrule.contains("FREQ=WEEKLY")) return null
            val byDay = rrule.split(";").firstOrNull { it.startsWith("BYDAY=") }?.removePrefix("BYDAY=") ?: return null
            // "1MO" 같은 접두 숫자는 월간 규칙에서 쓰이므로 요일 두 글자만 본다.
            return byDay.split(",").mapNotNull { BYDAY[it.takeLast(2)] }.toSet().takeIf { it.isNotEmpty() }
        }
    }
}
