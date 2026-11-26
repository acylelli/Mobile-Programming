package com.example.routealarm.shared

import kotlinx.datetime.LocalDate

/**
 * 한국 공휴일 표(대체공휴일·선거일 포함). 반복 알람의 "공휴일에는 울리지 않기"에 쓴다.
 *
 * 공공데이터 특일 API 는 서비스 키가 필요하고 알람은 오프라인에서도 정확해야 해서
 * 우주항공청 월력요항과 인사혁신처 발표를 기준으로 표를 넣어 둔다. 해마다 다음 해 표를 추가해야 한다.
 */
object KoreanHolidays {

    private val table: Map<LocalDate, String> = buildMap {
        // 2026
        put(2026, 1, 1, "신정")
        put(2026, 2, 16, "설날 연휴"); put(2026, 2, 17, "설날"); put(2026, 2, 18, "설날 연휴")
        put(2026, 3, 1, "삼일절"); put(2026, 3, 2, "대체공휴일(삼일절)")
        put(2026, 5, 5, "어린이날")
        put(2026, 5, 24, "부처님오신날"); put(2026, 5, 25, "대체공휴일(부처님오신날)")
        put(2026, 6, 3, "지방선거일")
        put(2026, 6, 6, "현충일")
        put(2026, 7, 17, "제헌절")
        put(2026, 8, 15, "광복절"); put(2026, 8, 17, "대체공휴일(광복절)")
        put(2026, 9, 24, "추석 연휴"); put(2026, 9, 25, "추석"); put(2026, 9, 26, "추석 연휴")
        put(2026, 10, 3, "개천절"); put(2026, 10, 5, "대체공휴일(개천절)")
        put(2026, 10, 9, "한글날")
        put(2026, 12, 25, "성탄절")
        // 2027
        put(2027, 1, 1, "신정")
        put(2027, 2, 6, "설날 연휴"); put(2027, 2, 7, "설날"); put(2027, 2, 8, "설날 연휴"); put(2027, 2, 9, "대체공휴일(설날)")
        put(2027, 3, 1, "삼일절")
        put(2027, 5, 1, "노동절"); put(2027, 5, 3, "대체공휴일(노동절)")
        put(2027, 5, 5, "어린이날")
        put(2027, 5, 13, "부처님오신날")
        put(2027, 6, 6, "현충일")
        put(2027, 7, 17, "제헌절"); put(2027, 7, 19, "대체공휴일(제헌절)")
        put(2027, 8, 15, "광복절"); put(2027, 8, 16, "대체공휴일(광복절)")
        put(2027, 9, 14, "추석 연휴"); put(2027, 9, 15, "추석"); put(2027, 9, 16, "추석 연휴")
        put(2027, 10, 3, "개천절"); put(2027, 10, 4, "대체공휴일(개천절)")
        put(2027, 10, 9, "한글날"); put(2027, 10, 11, "대체공휴일(한글날)")
        put(2027, 12, 25, "성탄절"); put(2027, 12, 27, "대체공휴일(성탄절)")
    }

    private fun MutableMap<LocalDate, String>.put(year: Int, month: Int, day: Int, name: String) {
        put(LocalDate(year, month, day), name)
    }

    fun isHoliday(date: LocalDate): Boolean = date in table

    fun name(date: LocalDate): String? = table[date]

    /** 표에 들어 있는 마지막 해. 화면에서 "언제까지 반영되는지" 알릴 때 쓴다. */
    val lastCoveredYear: Int get() = table.keys.maxOf { it.year }
}
