package com.example.routealarm.presentation.common

import androidx.compose.ui.graphics.Color
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.shared.routeline.KoreanText
import com.example.routealarm.shared.routeline.LineColors

/*
 * 노선색은 공식 노선색으로 고정한다. 컬러 테마를 바꿔도 2호선은 초록, 8호선은 분홍으로 보여야
 * 역 안내판과 같은 색으로 경로를 읽을 수 있다(UI-F-002 "노선색 고정 안내").
 */

/** 노선 이름으로 공식 노선색을 찾는다. 표는 shared 모듈([LineColors])에 있어 iOS 앱도 같은 색을 쓴다. */
fun transitLineColor(type: SegmentType?, lineName: String?): Color = Color(LineColors.argb(type?.name, lineName))

/** 구간 칩 배경: 노선색을 옅게 깐다. 글자는 노선색 그대로 쓴다. */
fun transitLineTint(color: Color): Color = color.copy(alpha = 0.13f)

/** 설계서 예시 화면(온보딩, 테마 미리보기)에 쓰는 대표 노선색 */
object SampleLineColors {
    val Line8 = Color(0xFFE6186C)
    val Line2 = Color(0xFF00A84D)
}

/** "2호선으로", "버스로" 처럼 방향 조사 (으)로 를 붙인다(규칙은 shared [KoreanText]). */
fun withDirectionParticle(word: String): String = KoreanText.withDirectionParticle(word)

/** "잠실역" → "잠실" (알람 화면의 짧은 환승 표기용) */
fun stationShortName(name: String): String = name.removeSuffix("역").ifBlank { name }
