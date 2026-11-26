package com.example.routealarm.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * 시스템 기본 서체(숫자·영문 Roboto, 한국어 Noto Sans CJK)를 사용한다.
 * 제목은 굵게, 시각은 아이폰 시계처럼 크고 보통 굵기(Regular), 설명은 보통 굵기의 회색으로 대비를 준다.
 */
private val Default = FontFamily.Default

/** 시각 표시에 쓰는 고정폭 숫자. 07:15 와 08:50 의 자릿수가 세로로 맞는다. */
private const val TABULAR = "tnum"

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR),
    displaySmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, lineHeight = 31.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp),
)

/** Material3 역할에 맞지 않는 화면 전용 글자 스타일 */
object AppType {
    /** 알람 화면의 현재 시각 */
    val RingTime = TextStyle(fontFamily = Default, fontWeight = FontWeight.Light, fontSize = 88.sp, lineHeight = 92.sp, letterSpacing = (-3).sp, fontFeatureSettings = TABULAR)

    /** 알람 목록의 기상 시각(아이폰 시계처럼 크고 가는 글씨) */
    val ListTime = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR)

    /** 알람 목록 시각 앞의 오전/오후 */
    val ListMeridiem = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 32.sp)

    /** 경로 타임라인의 지점 시각 */
    val NodeTime = TextStyle(fontFamily = Default, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR)

    /** 가격, 변경 전후 시각처럼 굵게 강조하는 숫자 */
    val Figure = TextStyle(fontFamily = Default, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR)

    /** 주 행동 버튼 */
    val Button = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp)

    /** 하단 탭 라벨 */
    val Tab = TextStyle(fontFamily = Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
}
