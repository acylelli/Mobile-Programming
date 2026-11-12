package com.example.routealarm.presentation.common

import androidx.compose.ui.graphics.Color
import com.example.routealarm.domain.model.SegmentType

/*
 * 노선색은 공식 노선색으로 고정한다. 컬러 테마를 바꿔도 2호선은 초록, 8호선은 분홍으로 보여야
 * 역 안내판과 같은 색으로 경로를 읽을 수 있다(UI-F-002 "노선색 고정 안내").
 */

private val SUBWAY_LINES: List<Pair<String, Color>> = listOf(
    "신분당" to Color(0xFFD4003B),
    "수인분당" to Color(0xFFF5A200),
    "경의중앙" to Color(0xFF77C4A3),
    "공항철도" to Color(0xFF0090D2),
    "경춘" to Color(0xFF0C8E72),
    "경강" to Color(0xFF003DA5),
    "서해" to Color(0xFF81A914),
    "우이신설" to Color(0xFFB0CE18),
    "신림" to Color(0xFF6789CA),
    "김포골드" to Color(0xFFA17800),
    "에버라인" to Color(0xFF509F22),
    "의정부" to Color(0xFFFDA600),
    "GTX" to Color(0xFF9A6292),
    "인천1" to Color(0xFF7CA8D5),
    "인천2" to Color(0xFFED8B00),
    "1호선" to Color(0xFF0052A4),
    "2호선" to Color(0xFF00A84D),
    "3호선" to Color(0xFFEF7C1C),
    "4호선" to Color(0xFF00A5DE),
    "5호선" to Color(0xFF996CAC),
    "6호선" to Color(0xFFCD7C2F),
    "7호선" to Color(0xFF747F00),
    "8호선" to Color(0xFFE6186C),
    "9호선" to Color(0xFFBDB092),
)

private val BUS_KINDS: List<Pair<String, Color>> = listOf(
    "광역" to Color(0xFFE60012),
    "간선" to Color(0xFF0068B7),
    "지선" to Color(0xFF53B332),
    "마을" to Color(0xFF53B332),
    "순환" to Color(0xFFF2B70A),
    "공항" to Color(0xFF3D5BAB),
)

private val DEFAULT_SUBWAY = Color(0xFF5B6B8C)
private val DEFAULT_BUS = Color(0xFF0068B7)
private val DEFAULT_TRAIN = Color(0xFF204C8C)
private val DEFAULT_OTHER = Color(0xFF6B6B6B)

/** 노선 이름으로 공식 노선색을 찾는다. 알 수 없는 노선은 교통수단별 기본색을 쓴다. */
fun transitLineColor(type: SegmentType?, lineName: String?): Color {
    val name = lineName.orEmpty().replace(" ", "")
    return when (type) {
        SegmentType.SUBWAY -> SUBWAY_LINES.firstOrNull { name.contains(it.first) }?.second ?: DEFAULT_SUBWAY
        SegmentType.BUS -> BUS_KINDS.firstOrNull { name.contains(it.first) }?.second ?: DEFAULT_BUS
        SegmentType.TRAIN -> DEFAULT_TRAIN
        else -> SUBWAY_LINES.firstOrNull { name.isNotEmpty() && name.contains(it.first) }?.second ?: DEFAULT_OTHER
    }
}

/** 구간 칩 배경: 노선색을 옅게 깐다. 글자는 노선색 그대로 쓴다. */
fun transitLineTint(color: Color): Color = color.copy(alpha = 0.13f)

/** 설계서 예시 화면(온보딩, 테마 미리보기)에 쓰는 대표 노선색 */
object SampleLineColors {
    val Line8 = Color(0xFFE6186C)
    val Line2 = Color(0xFF00A84D)
}

/**
 * "2호선으로", "버스로" 처럼 방향 조사 (으)로 를 붙인다.
 * 받침이 없거나 ㄹ 받침이면 "로", 그 외 받침이면 "으로".
 */
fun withDirectionParticle(word: String): String {
    val last = word.lastOrNull() ?: return word
    val useRo = when (last) {
        in '가'..'힣' -> {
            val jongseong = (last.code - 0xAC00) % 28
            jongseong == 0 || jongseong == 8 // 8 = ㄹ
        }
        // 숫자는 읽는 소리 기준: 0(영) 3(삼) 6(육) 은 받침이 있다.
        in '0'..'9' -> last !in setOf('0', '3', '6')
        else -> true
    }
    return word + if (useRo) "로" else "으로"
}

/** "잠실역" → "잠실" (알람 화면의 짧은 환승 표기용) */
fun stationShortName(name: String): String = name.removeSuffix("역").ifBlank { name }
