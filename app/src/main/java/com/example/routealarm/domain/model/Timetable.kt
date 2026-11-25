package com.example.routealarm.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * 학교 시간표의 수업 하나(SFR 시간표 연동).
 *
 * 알람에 필요한 것은 "요일마다 첫 수업이 몇 시에 시작하는가"뿐이라 시작 시각이 중심이다.
 * 끝나는 시각과 강의실은 화면에서 수업을 알아보기 쉽게 하려고 함께 둔다(사진 인식에서는 알 수 없을 수 있다).
 */
data class TimetableClass(
    val id: String,
    val name: String,
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime? = null,
    val room: String? = null,
)

/**
 * 시간표로 만들 알람 하나: 첫 수업 시각이 같은 요일들을 묶은 반복 일정.
 * 예) 월 12:00 / 수·목 13:30 / 금 09:00 → 알람 3개
 */
data class TimetableAlarmProposal(
    val arrival: LocalTime,
    val days: Set<DayOfWeek>,
    /** 그 요일들의 첫 수업 이름(화면 표시용, 요일 순) */
    val firstClassNames: List<String>,
)
