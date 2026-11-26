package com.example.routealarm.shared.timetable

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.math.abs
import kotlin.math.roundToInt

/** 글자 인식 결과의 한 단어. 좌표는 이미지 픽셀 기준 */
data class OcrWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
}

/** 글자 인식 결과의 한 줄. 줄을 이루는 단어 위치도 함께 둔다(요일·시각 머리글은 단어 단위로 찾는다). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val words: List<OcrWord>) {
    val height: Int get() = bottom - top
}

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
 *
 * 1. 요일 머리글(월 화 수 목 금 …)의 가로 위치로 요일 열을 정한다.
 * 2. 왼쪽 시각 숫자의 세로 위치로 "세로 픽셀 → 시각" 비례식을 만든다. 12시간제(… 11, 12, 1, 2 …)는 숫자가 작아지는
 *    지점부터 오후로 본다.
 * 3. 열마다 위에서부터 줄을 읽어, 강의실처럼 생긴 줄(탐구관307)이 나오거나 줄 사이가 크게 벌어지면 수업 하나로 끊는다.
 *    긴 이름이 여러 줄로 나뉜 경우(English Se / ntence Ana / lysis) 줄을 이어 붙인다.
 * 4. 수업 이름 첫 줄의 위치를 시각으로 바꾸고 15분 단위로 맞춘다. 칸 위쪽 여백과 시각 숫자의 위쪽 여백이 비슷해서
 *    같은 기준(글자 위쪽)으로 계산하면 여백이 서로 상쇄된다.
 *
 * 인식이 완벽하지 않으므로 결과는 항상 사용자가 확인·수정한 뒤 저장한다.
 */
object TimetableImageParser {

    private val DAY_LABELS = mapOf(
        "월" to DayOfWeek.MONDAY, "화" to DayOfWeek.TUESDAY, "수" to DayOfWeek.WEDNESDAY, "목" to DayOfWeek.THURSDAY,
        "금" to DayOfWeek.FRIDAY, "토" to DayOfWeek.SATURDAY, "일" to DayOfWeek.SUNDAY,
    )

    /** 탐구관307, 창의관 509, B101, 공학관-201 같은 강의실 표기 */
    private val ROOM = Regex("""^[가-힣A-Za-z]*[-\s]?[A-Za-z]?\d{2,4}[A-Za-z]?(호)?$""")
    private val HOUR = Regex("""^\d{1,2}$""")
    private const val ROUND_MINUTES = 15

    fun parse(lines: List<OcrLine>): TimetableParseResult {
        val words = lines.flatMap { it.words }
        val dayWords = words.filter { it.text.trim() in DAY_LABELS }
            // 같은 요일 글자가 여러 번 나오면(수업 이름의 한 글자 등) 가장 위의 것이 머리글이다.
            .groupBy { it.text.trim() }.map { (_, list) -> list.minBy { it.top } }
        if (dayWords.size < 2) return TimetableParseResult.NoDayHeader
        val headerTop = dayWords.minOf { it.top }
        val headerWords = dayWords.filter { abs(it.top - headerTop) < (it.bottom - it.top) * 2 }
        if (headerWords.size < 2) return TimetableParseResult.NoDayHeader
        val headerBottom = headerWords.maxOf { it.bottom }

        // 요일 열: 인식된 머리글(일부 요일은 못 읽을 수 있다)로 "요일 번호 → 가로 위치" 직선을 맞춰 빠진 열까지 채운다.
        val known = headerWords.map { DAY_LABELS.getValue(it.text.trim()).isoDayNumber to it.centerX.toDouble() }
        val (columnStep, columnAt) = fitLine(known) ?: return TimetableParseResult.NoDayHeader
        if (columnStep <= 0) return TimetableParseResult.NoDayHeader
        val firstDay = known.minOf { it.first }
        val contentRight = lines.filter { it.top > headerBottom }.maxOfOrNull { it.right } ?: 0
        val columns = (firstDay..DayOfWeek.SUNDAY.isoDayNumber)
            .map { DayOfWeek(it) to columnAt(it.toDouble()) }
            .filter { (day, x) -> day.isoDayNumber <= known.maxOf { it.first } || x - columnStep / 2 < contentRight }
        val gridLeft = columns.first().second - columnStep / 2

        val hourWords = words
            .filter { HOUR.matches(it.text.trim()) && it.right <= gridLeft + columnStep / 4 && it.top > headerBottom }
            .sortedBy { it.top }
        if (hourWords.size < 2) return TimetableParseResult.NoHourLabels
        val hours = to24Hour(hourWords.map { it.text.trim().toInt() })
        // 시각 숫자 몇 개를 못 읽어도 되도록 "시각 → 세로 위치" 도 직선으로 맞춘다.
        val (pxPerHour, yAt) = fitLine(hours.zip(hourWords.map { it.top.toDouble() })) ?: return TimetableParseResult.NoHourLabels
        if (pxPerHour <= 0) return TimetableParseResult.NoHourLabels
        val y0 = yAt(0.0)

        fun timeAt(y: Int): LocalTime? {
            val minutes = (y - y0) / pxPerHour * 60
            val rounded = (minutes / ROUND_MINUTES).roundToInt() * ROUND_MINUTES
            return if (rounded in 0 until 24 * 60) LocalTime(rounded / 60, rounded % 60) else null
        }

        fun columnOf(line: OcrLine): DayOfWeek? {
            // 칸 안의 글자는 왼쪽 정렬이라 줄의 왼쪽 끝으로 열을 정한다.
            val x = line.left + columnStep / 8
            return columns.minByOrNull { abs(it.second - x) }
                ?.takeIf { abs(it.second - x) <= columnStep * 0.6 }?.first
        }

        // 칸 안의 글자만 남긴다. 시각 숫자가 옆 수업 이름과 한 줄로 붙어 나오면("12 Job English") 숫자만 떼어 낸다.
        val hourWordSet = hourWords.toSet()
        val contentLines = lines
            .filter { it.top > headerBottom }
            .mapNotNull { line -> line.withoutWords(hourWordSet) }
            .filter { it.right > gridLeft + columnStep / 4 }
        val classes = contentLines
            .mapNotNull { line -> columnOf(line)?.let { it to line } }
            .groupBy({ it.first }, { it.second })
            .flatMap { (day, dayLines) -> splitIntoClasses(dayLines.sortedBy { it.top }).mapNotNull { it.toClass(day, ::timeAt) } }
            .sortedWith(compareBy({ it.day }, { it.start }))
        return TimetableParseResult.Success(classes)
    }

    /** 점들에 맞는 직선 y = a + b·x 의 (기울기 b, x → y 함수). 서로 다른 x 가 두 개 이상 있어야 한다. */
    private fun fitLine(points: List<Pair<Number, Double>>): Pair<Double, (Double) -> Double>? {
        val xs = points.map { it.first.toDouble() }
        if (xs.distinct().size < 2) return null
        val ys = points.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
        val intercept = my - slope * mx
        return slope to { x: Double -> intercept + slope * x }
    }

    /** [removed] 단어를 뺀 줄. 칸 테두리가 글자로 읽힌 앞뒤 잡문자(|, [, ])도 지운다. 남는 글자가 없으면 null */
    private fun OcrLine.withoutWords(removed: Set<OcrWord>): OcrLine? {
        val kept = if (words.isEmpty()) null else words.filterNot { it in removed }
        val text = (kept?.joinToString(" ") { it.text } ?: this.text).trim().trim('|', '[', ']', '｜', ' ')
        if (text.isEmpty()) return null
        if (kept.isNullOrEmpty() || kept.size == words.size) return copy(text = text)
        return OcrLine(text, kept.minOf { it.left }, kept.minOf { it.top }, kept.maxOf { it.right }, kept.maxOf { it.bottom }, kept)
    }

    /** 9 10 11 12 1 2 … 처럼 앞 숫자보다 작아지면 그때부터 12를 더해 24시간제로 바꾼다. */
    fun to24Hour(values: List<Int>): List<Int> {
        var add = 0
        var prev = -1
        return values.map { value ->
            if (prev >= 0 && value + add <= prev) add += 12
            prev = value + add
            prev
        }
    }

    private class Block(val lines: List<OcrLine>)

    /** 강의실 줄이 나오거나 줄 사이가 크게 벌어지면 수업 하나로 끊는다. */
    private fun splitIntoClasses(lines: List<OcrLine>): List<Block> {
        val blocks = mutableListOf<Block>()
        var current = mutableListOf<OcrLine>()
        for (line in lines) {
            val prev = current.lastOrNull()
            val gap = prev?.let { line.top - it.bottom } ?: 0
            val farApart = prev != null && gap > prev.height * 1.5
            if (prev != null && (isRoom(prev.text) || farApart)) {
                blocks += Block(current)
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) blocks += Block(current)
        return blocks
    }

    private fun Block.toClass(day: DayOfWeek, timeAt: (Int) -> LocalTime?): ParsedClass? {
        val roomLine = lines.lastOrNull()?.takeIf { isRoom(it.text) }
        val nameLines = if (roomLine != null) lines.dropLast(1) else lines
        // 긴 이름은 단어 중간에서 줄이 바뀐다(English Se / ntence). 줄 사이에 공백을 넣지 않고 그대로 잇는다.
        val name = nameLines.joinToString("") { it.text.trim() }.trim()
        if (name.isEmpty()) return null
        val start = timeAt(lines.first().top) ?: return null
        return ParsedClass(name = name, day = day, start = start, room = roomLine?.text?.replace(" ", ""))
    }

    private fun isRoom(text: String): Boolean = ROOM.matches(text.trim())
}
