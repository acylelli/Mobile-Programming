package com.example.routealarm.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * 타입 안전 Navigation 라우트.
 * 상세/수정 화면에는 ID 만 전달하고 데이터는 Repository 에서 다시 읽는다(Route Argument 남용 금지).
 */
sealed interface Route {
    @Serializable data object Splash : Route
    @Serializable data object Onboarding : Route
    @Serializable data object Permission : Route

    /** 하단 탭을 감싸는 그래프 */
    @Serializable data object Main : Route
    @Serializable data object Home : Route
    @Serializable data object AlarmList : Route
    @Serializable data object Settings : Route

    /** 단계별 일정 생성. editScheduleId 가 있으면 수정 모드 */
    @Serializable data class CreateSchedule(val editScheduleId: Long? = null) : Route

    /** 장소 검색. resultKey 로 출발지/도착지 중 어디에 결과를 돌려줄지 구분 */
    @Serializable data class PlaceSearch(val resultKey: String) : Route

    @Serializable data class ScheduleDetail(val scheduleId: Long) : Route
}

/** 장소 검색 결과를 이전 화면의 SavedStateHandle 로 돌려줄 때 쓰는 키 */
object PlaceSearchResult {
    const val KEY_ORIGIN = "place_result_origin"
    const val KEY_DESTINATION = "place_result_destination"
}
