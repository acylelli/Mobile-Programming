package com.example.routealarm.domain.repository

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.TimetableClass
import com.example.routealarm.domain.policy.TimetableParseResult
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant

/** 사용자가 입력하거나 가져온 학교 시간표. 기기 안에만 저장한다. */
interface TimetableRepository {
    val classes: Flow<List<TimetableClass>>

    suspend fun upsert(item: TimetableClass)

    suspend fun addAll(items: List<TimetableClass>)

    suspend fun replaceAll(items: List<TimetableClass>)

    suspend fun remove(id: String)

    /** 시간표로 만든 알람(일정 id). 학기가 바뀌어 다시 만들 때 예전 알람을 지우는 데 쓴다. */
    val alarmScheduleIds: Flow<List<Long>>

    suspend fun setAlarmScheduleIds(ids: List<Long>)
}

/**
 * 시간표 사진에서 글자를 읽어 수업 목록으로 해석한다(기기 안에서 처리, 사진을 서버로 보내지 않음).
 * Domain 은 Android 에 의존하지 않으므로 사진은 content:// 주소 문자열로 받는다.
 */
interface TimetableImageRecognizer {
    suspend fun recognize(imageUri: String): AppResult<TimetableParseResult>
}

/** 기기 캘린더의 일정 하나 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: Instant,
    val location: String?,
    /** 매주 반복 일정이면 반복 요일. 한 번뿐인 일정이면 비어 있다. */
    val weeklyDays: Set<DayOfWeek>,
)

/** 기기 캘린더(Google·삼성 캘린더 등이 동기화해 둔 일정)를 읽는다. 쓰지는 않는다. */
interface CalendarRepository {
    fun hasPermission(): Boolean

    /** 지금부터 [days]일 안의 일정. 하루 종일 일정은 도착 시각이 없어서 뺀다. */
    suspend fun upcomingEvents(days: Long): AppResult<List<CalendarEvent>>
}
