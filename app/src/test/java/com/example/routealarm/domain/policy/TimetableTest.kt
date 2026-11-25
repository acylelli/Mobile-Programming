package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.TimetableClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime

/**
 * 시간표 사진 해석과 알람 제안.
 * 사진 해석은 실제 에브리타임 시간표 캡처(2026년 2학기, 다크 모드, 1206×1509)의 글자 위치를 옮겨 적어 검증한다.
 */
class TimetableTest {

    private fun word(text: String, left: Int, top: Int, right: Int, bottom: Int) = OcrWord(text, left, top, right, bottom)

    /** 한 줄짜리 글자(단어 하나 또는 여러 개) */
    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int): OcrLine {
        val parts = text.split(" ")
        val width = (right - left) / parts.size
        return OcrLine(text, left, top, right, bottom, parts.mapIndexed { i, p -> word(p, left + i * width, top, left + (i + 1) * width, bottom) })
    }

    /** 첨부한 시간표 사진에서 글자 인식이 돌려주는 줄과 위치(이미지 픽셀) */
    private val everytimeCapture = listOf(
        // 학기 탭(머리글 위라 무시돼야 한다)
        line("2026년 2학기", 110, 30, 345, 65),
        line("2025년 겨울학기", 420, 30, 690, 65),
        // 요일 머리글
        line("월", 183, 185, 207, 213), line("화", 403, 185, 427, 213), line("수", 623, 185, 647, 213),
        line("목", 842, 185, 866, 213), line("금", 1062, 185, 1086, 213),
        // 왼쪽 시각(12 다음은 1)
        line("9", 64, 239, 82, 267), line("10", 52, 374, 82, 402), line("11", 54, 509, 82, 537),
        line("12", 52, 644, 82, 672), line("1", 68, 779, 82, 807), line("2", 66, 914, 82, 942),
        line("3", 66, 1049, 82, 1077), line("4", 65, 1184, 82, 1212), line("5", 66, 1319, 82, 1347),
        // 금
        line("AI와 UXD", 973, 244, 1125, 278), line("창의관509", 973, 285, 1105, 315),
        // 월
        line("Job English", 94, 650, 295, 684), line("탐구관307", 94, 690, 225, 720),
        line("실용영어청취", 94, 855, 278, 889), line("탐구관307", 94, 895, 225, 925),
        line("English Se", 94, 1261, 276, 1295), line("ntence Ana", 94, 1304, 291, 1338), line("lysis", 94, 1347, 171, 1381),
        line("상상관503", 94, 1386, 227, 1416),
        // 수
        line("실용영어청취", 533, 855, 717, 889), line("탐구관307", 533, 895, 664, 925),
        line("English Se", 533, 1261, 715, 1295), line("ntence Ana", 533, 1304, 730, 1338), line("lysis", 533, 1347, 610, 1381),
        line("상상관604", 533, 1386, 667, 1416),
        // 목
        line("Job English", 753, 855, 954, 889), line("탐구관307", 753, 895, 884, 925),
        line("커뮤니케이션", 753, 1055, 937, 1089), line("그래픽-캡스톤", 753, 1098, 954, 1132), line("디자인", 753, 1141, 845, 1175),
        line("창의관304", 753, 1182, 886, 1212),
    )

    @Test
    fun `에브리타임 시간표 사진에서 요일·시작 시각·이름·강의실을 읽는다`() {
        val result = TimetableImageParser.parse(everytimeCapture) as TimetableParseResult.Success

        assertEquals(
            listOf(
                ParsedClass("Job English", MONDAY, LocalTime.of(12, 0), "탐구관307"),
                ParsedClass("실용영어청취", MONDAY, LocalTime.of(13, 30), "탐구관307"),
                ParsedClass("English Sentence Analysis", MONDAY, LocalTime.of(16, 30), "상상관503"),
                ParsedClass("실용영어청취", WEDNESDAY, LocalTime.of(13, 30), "탐구관307"),
                ParsedClass("English Sentence Analysis", WEDNESDAY, LocalTime.of(16, 30), "상상관604"),
                ParsedClass("Job English", THURSDAY, LocalTime.of(13, 30), "탐구관307"),
                ParsedClass("커뮤니케이션그래픽-캡스톤디자인", THURSDAY, LocalTime.of(15, 0), "창의관304"),
                ParsedClass("AI와 UXD", FRIDAY, LocalTime.of(9, 0), "창의관509"),
            ),
            result.classes,
        )
    }

    /**
     * 같은 사진을 에뮬레이터의 ML Kit 한국어 인식으로 실제로 읽은 결과(디버그 로그를 그대로 옮김).
     * 위 정리된 데이터와 달리: "수"·"목" 머리글과 시각 "9"·"5"를 못 읽었고, "12"가 수업 이름과 한 줄로 붙었으며,
     * 칸 테두리가 "|" 로 읽혔다.
     */
    private val mlKitOutput = listOf(
        line("10", 52, 377, 79, 399), line("11", 57, 513, 79, 534), line("1", 71, 783, 79, 803),
        line("12 Job English", 58, 644, 293, 685), line("탐구관307", 95, 692, 224, 718),
        line("2", 68, 918, 81, 939), line("3", 66, 1053, 80, 1074), line("2026년 2학기", 111, 29, 342, 64),
        line("4", 67, 1188, 81, 1208), line("월", 183, 186, 206, 212),
        line("실용영어청취", 95, 855, 276, 887), line("탐구관307", 95, 897, 224, 923),
        line("English Se", 97, 1262, 275, 1296), line("ntence Ana", 95, 1303, 287, 1336), line("lysis", 95, 1346, 168, 1381),
        line("상상관503", 94, 1385, 223, 1414), line("2025년 겨울학기", 422, 26, 686, 65), line("화", 403, 186, 426, 212),
        line("실용영어청취", 534, 855, 715, 891), line("탐구관307", 522, 895, 674, 924),
        line("English Se", 535, 1262, 713, 1295), line("ntence Ana", 535, 1307, 728, 1332), line("lysis", 538, 1346, 610, 1380),
        line("상상관604", 533, 1388, 666, 1414), line("2025년 2학기", 768, 27, 988, 66),
        line("|Job English", 754, 855, 953, 891), line("탐구관307", 754, 897, 883, 923),
        line("커뮤니케이션", 753, 1055, 935, 1087), line("그래픽-캡스톤", 753, 1098, 954, 1130), line("디자인", 755, 1141, 842, 1173),
        line("창의관304", 753, 1183, 886, 1209), line("2025년", 1072, 31, 1193, 63), line("금", 1062, 187, 1086, 212),
        line("AI와 UXD", 972, 245, 1124, 276), line("창의관509", 972, 286, 1104, 312),
    )

    @Test
    fun `실제 글자 인식 결과처럼 요일·시각 일부가 빠지고 글자가 붙어 나와도 같은 시간표를 읽는다`() {
        val fromMlKit = TimetableImageParser.parse(mlKitOutput) as TimetableParseResult.Success
        val expected = (TimetableImageParser.parse(everytimeCapture) as TimetableParseResult.Success).classes

        assertEquals(expected, fromMlKit.classes)
    }

    @Test
    fun `요일 머리글이나 시각 표시가 없으면 해석하지 않는다`() {
        assertEquals(TimetableParseResult.NoDayHeader, TimetableImageParser.parse(listOf(line("아무 글자", 0, 0, 100, 30))))
        val headerOnly = everytimeCapture.filter { it.text in setOf("월", "화", "수", "목", "금") }
        assertEquals(TimetableParseResult.NoHourLabels, TimetableImageParser.parse(headerOnly))
    }

    @Test
    fun `12시간제 시각은 숫자가 작아지는 지점부터 오후로 본다`() {
        assertEquals(listOf(9, 10, 11, 12, 13, 14, 15), TimetableImageParser.to24Hour(listOf(9, 10, 11, 12, 1, 2, 3)))
        assertEquals(listOf(13, 14, 15), TimetableImageParser.to24Hour(listOf(13, 14, 15)))
    }

    @Test
    fun `요일마다 첫 수업 시각에 맞춰 알람을 제안하고, 시각이 같은 요일은 하나로 묶는다`() {
        val result = TimetableImageParser.parse(everytimeCapture) as TimetableParseResult.Success
        val classes = result.classes.mapIndexed { i, c -> TimetableClass("c$i", c.name, c.day, c.start, room = c.room) }

        val proposals = TimetablePolicy.proposals(classes)

        // 금 09:00 / 월 12:00 / 수·목 13:30 → 알람 3개
        assertEquals(3, proposals.size)
        assertEquals(LocalTime.of(9, 0) to setOf(FRIDAY), proposals[0].arrival to proposals[0].days)
        assertEquals(LocalTime.of(12, 0) to setOf(MONDAY), proposals[1].arrival to proposals[1].days)
        assertEquals(LocalTime.of(13, 30) to setOf(WEDNESDAY, THURSDAY), proposals[2].arrival to proposals[2].days)
        assertEquals(listOf("실용영어청취", "Job English"), proposals[2].firstClassNames)
    }

    @Test
    fun `수업이 없으면 제안도 없다`() {
        assertTrue(TimetablePolicy.proposals(emptyList()).isEmpty())
    }

    @Test
    fun `학기가 바뀌어 알람을 다시 만들면 새 알람이 모두 만들어졌을 때만 예전 알람을 지운다`() {
        assertEquals(
            TimetableAlarmReplacement(delete = listOf(1L, 2L), remember = listOf(10L, 11L)),
            TimetablePolicy.alarmReplacement(previous = listOf(1L, 2L), created = listOf(10L, 11L), anyFailed = false),
        )
    }

    @Test
    fun `하나라도 못 만들면 예전 알람을 남기고, 다음에 함께 바뀌도록 모두 기억한다`() {
        assertEquals(
            TimetableAlarmReplacement(delete = emptyList(), remember = listOf(1L, 2L, 10L)),
            TimetablePolicy.alarmReplacement(previous = listOf(1L, 2L), created = listOf(10L), anyFailed = true),
        )
        assertEquals(
            TimetableAlarmReplacement(delete = emptyList(), remember = listOf(1L)),
            TimetablePolicy.alarmReplacement(previous = listOf(1L), created = emptyList(), anyFailed = true),
        )
    }

    @Test
    fun `처음 만들 때는 지울 알람이 없다`() {
        assertEquals(
            TimetableAlarmReplacement(delete = emptyList(), remember = listOf(10L)),
            TimetablePolicy.alarmReplacement(previous = emptyList(), created = listOf(10L), anyFailed = false),
        )
    }
}
