package com.example.routealarm.domain.policy

import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalTime
import com.example.routealarm.shared.timetable.TimetableImageParser as SharedParser
import com.example.routealarm.shared.timetable.TimetableParseResult as SharedResult

import java.time.DayOfWeek
import java.time.LocalTime

/** 글자 인식 결과의 한 단어·한 줄. 해석기와 함께 shared 모듈에 있다(iOS Vision 결과도 같은 모양으로 넘긴다). */
typealias OcrWord = com.example.routealarm.shared.timetable.OcrWord
typealias OcrLine = com.example.routealarm.shared.timetable.OcrLine

/** 사진에서 읽은 수업 하나. 이름·강의실은 사용자가 확인 화면에서 고칠 수 있다. */
data class ParsedClass(val name: String, val day: DayOfWeek, val start: LocalTime, val room: String?)

sealed interface TimetableParseResult {
    data class Success(val classes: List<ParsedClass>) : TimetableParseResult

    /** 요일 머리글(월~금)을 찾지 못함: 시간표 사진이 아니거나 잘렸다 */
    data object NoDayHeader : TimetableParseResult

    /** 왼쪽 시각 표시(9, 10, 11 …)를 두 개 이상 찾지 못함: 수업 시각을 계산할 수 없다 */
    data object NoHourLabels : TimetableParseResult
}

/**
 * 에브리타임처럼 "위쪽에 요일, 왼쪽에 시각, 칸 안에 수업 이름과 강의실"인 시간표 사진을 해석한다.
 * 해석 규칙은 shared 모듈의 [SharedParser] 에 있고, 여기서는 결과를 java.time 으로 바꾼다.
 */
object TimetableImageParser {
    fun parse(lines: List<OcrLine>): TimetableParseResult = when (val result = SharedParser.parse(lines)) {
        is SharedResult.Success -> TimetableParseResult.Success(
            result.classes.map { ParsedClass(it.name, it.day.toJavaDayOfWeek(), it.start.toJavaLocalTime(), it.room) },
        )
        SharedResult.NoDayHeader -> TimetableParseResult.NoDayHeader
        SharedResult.NoHourLabels -> TimetableParseResult.NoHourLabels
    }

    internal fun to24Hour(values: List<Int>): List<Int> = SharedParser.to24Hour(values)
}
