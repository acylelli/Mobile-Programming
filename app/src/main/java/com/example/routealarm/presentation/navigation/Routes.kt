package com.example.routealarm.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * 타입 안전 Navigation 라우트. UI 설계서의 화면 ID 와 1:1 로 대응한다.
 * 상세/수정 화면에는 ID 만 전달하고 데이터는 Repository 에서 다시 읽는다(Route Argument 남용 금지).
 */
sealed interface Route {
    /** UI-A-001 온보딩, 권한 안내 */
    @Serializable data object Onboarding : Route

    /** UI-B-001 홈 (내일 경로) */
    @Serializable data object Home : Route

    /** UI-D-001 알람 목록 */
    @Serializable data object AlarmList : Route

    /** UI-F-001 설정 */
    @Serializable data object Settings : Route

    /** UI-C-001~006 새 일정 5단계 + 계산 미리보기. editScheduleId 가 있으면 수정 모드 */
    @Serializable data class CreateSchedule(val editScheduleId: Long? = null) : Route

    /** UI-D-002 일정 상세 */
    @Serializable data class ScheduleDetail(val scheduleId: Long) : Route

    /** UI-F-002 테마 선택 */
    @Serializable data object ThemeSelect : Route

    /** UI-E-002 단체 이용권 코드 입력 */
    @Serializable data object GroupPass : Route

    /** UI-F-003 구독 관리 */
    @Serializable data object Subscription : Route
}
