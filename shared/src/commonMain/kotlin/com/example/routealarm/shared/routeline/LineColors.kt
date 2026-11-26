package com.example.routealarm.shared.routeline

/**
 * 공식 노선색(ARGB). 컬러 테마를 바꿔도 2호선은 초록, 8호선은 분홍으로 보여야 역 안내판과 같은 색으로 경로를 읽을 수 있다.
 * 안드로이드(Compose Color)와 iOS(SwiftUI Color)가 같은 표를 쓴다.
 * mode 는 WALK, BUS, SUBWAY, TRAIN, ETC.
 */
object LineColors {
    private val SUBWAY_LINES: List<Pair<String, Long>> = listOf(
        "신분당" to 0xFFD4003B,
        "수인분당" to 0xFFF5A200,
        "경의중앙" to 0xFF77C4A3,
        "공항철도" to 0xFF0090D2,
        "경춘" to 0xFF0C8E72,
        "경강" to 0xFF003DA5,
        "서해" to 0xFF81A914,
        "우이신설" to 0xFFB0CE18,
        "신림" to 0xFF6789CA,
        "김포골드" to 0xFFA17800,
        "에버라인" to 0xFF509F22,
        "의정부" to 0xFFFDA600,
        "GTX" to 0xFF9A6292,
        "인천1" to 0xFF7CA8D5,
        "인천2" to 0xFFED8B00,
        "1호선" to 0xFF0052A4,
        "2호선" to 0xFF00A84D,
        "3호선" to 0xFFEF7C1C,
        "4호선" to 0xFF00A5DE,
        "5호선" to 0xFF996CAC,
        "6호선" to 0xFFCD7C2F,
        "7호선" to 0xFF747F00,
        "8호선" to 0xFFE6186C,
        "9호선" to 0xFFBDB092,
    )

    private val BUS_KINDS: List<Pair<String, Long>> = listOf(
        "광역" to 0xFFE60012,
        "간선" to 0xFF0068B7,
        "지선" to 0xFF53B332,
        "마을" to 0xFF53B332,
        "순환" to 0xFFF2B70A,
        "공항" to 0xFF3D5BAB,
    )

    const val DEFAULT_SUBWAY = 0xFF5B6B8C
    const val DEFAULT_BUS = 0xFF0068B7
    const val DEFAULT_TRAIN = 0xFF204C8C
    const val DEFAULT_OTHER = 0xFF6B6B6B

    /** 노선 이름으로 공식 노선색을 찾는다. 알 수 없는 노선은 교통수단별 기본색 */
    fun argb(mode: String?, lineName: String?): Long {
        val name = lineName.orEmpty().replace(" ", "")
        return when (mode) {
            "SUBWAY" -> SUBWAY_LINES.firstOrNull { name.contains(it.first) }?.second ?: DEFAULT_SUBWAY
            "BUS" -> BUS_KINDS.firstOrNull { name.contains(it.first) }?.second ?: DEFAULT_BUS
            "TRAIN" -> DEFAULT_TRAIN
            else -> SUBWAY_LINES.firstOrNull { name.isNotEmpty() && name.contains(it.first) }?.second ?: DEFAULT_OTHER
        }
    }
}
