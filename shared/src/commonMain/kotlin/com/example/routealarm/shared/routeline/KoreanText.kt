package com.example.routealarm.shared.routeline

/** 노선도 문구에 쓰는 한국어 규칙 */
object KoreanText {
    /**
     * "2호선으로", "버스로" 처럼 방향 조사 (으)로 를 붙인다.
     * 받침이 없거나 ㄹ 받침이면 "로", 그 외 받침이면 "으로". 숫자는 읽는 소리 기준(0 영, 3 삼, 6 육은 받침이 있다).
     */
    fun withDirectionParticle(word: String): String {
        val last = word.lastOrNull() ?: return word
        val useRo = when (last) {
            in '가'..'힣' -> {
                val jongseong = (last.code - 0xAC00) % 28
                jongseong == 0 || jongseong == 8 // 8 = ㄹ
            }
            in '0'..'9' -> last !in setOf('0', '3', '6')
            else -> true
        }
        return word + if (useRo) "로" else "으로"
    }

    /** 노선 이름이 없으면 교통수단 이름으로 대신한다. */
    fun lineDisplayName(mode: String?, lineName: String?): String = lineName?.takeIf { it.isNotBlank() } ?: when (mode) {
        "BUS" -> "버스"
        "SUBWAY" -> "지하철"
        "TRAIN" -> "기차"
        else -> "이동"
    }

    /** 45분, 1시간, 1시간 20분 */
    fun minutes(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        return when {
            m < 60 -> "${m}분"
            m % 60 == 0 -> "${m / 60}시간"
            else -> "${m / 60}시간 ${m % 60}분"
        }
    }
}
