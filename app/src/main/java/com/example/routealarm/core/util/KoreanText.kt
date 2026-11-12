package com.example.routealarm.core.util

/** 앞뒤 글자 사이에서 줄을 바꾸지 못하게 하는 폭 없는 문자(WORD JOINER) */
private const val WORD_JOINER = '\u2060'

/**
 * 한국어 문장이 낱말 중간에서 줄바꿈되지 않게 한다(CSS 의 word-break: keep-all 과 같은 효과).
 *
 * Android 의 줄바꿈은 한글 음절 사이 어디서든 끊을 수 있어 "기 / 상-출발" 처럼 낱말이 쪼개진다.
 * 띄어쓰기가 아닌 글자 사이마다 WORD JOINER 를 넣어 띄어쓰기에서만 줄이 바뀌게 한다.
 * 화면에 보이는 글자와 길이는 그대로다.
 */
fun String.keepWords(): String {
    if (length < 2) return this
    val source = this
    return buildString(length * 2) {
        for (index in source.indices) {
            val current = source[index]
            append(current)
            val next = source.getOrNull(index + 1) ?: break
            // 이모지 등 서로게이트 쌍 사이에는 끼워 넣지 않는다.
            if (!current.isWhitespace() && !next.isWhitespace() && !current.isHighSurrogate()) append(WORD_JOINER)
        }
    }
}
