package com.example.routealarm.presentation

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.domain.model.AdjustmentKind
import com.example.routealarm.domain.model.TimetableClass
import com.example.routealarm.domain.policy.TimetablePolicy
import com.example.routealarm.domain.repository.CalendarEvent
import com.example.routealarm.presentation.calendar.CalendarImportScreen
import com.example.routealarm.presentation.calendar.CalendarUiState
import com.example.routealarm.presentation.home.PendingAdjustmentCard
import com.example.routealarm.presentation.timetable.TimetableScreen
import com.example.routealarm.presentation.timetable.TimetableUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * 상태만 받는 화면(Screen) 컴포저블을 실제 기기에서 그려, 보이는 내용과 버튼이 부르는 동작을 확인한다.
 * ViewModel·Hilt 없이 상태와 콜백만 넣는다.
 */
@RunWith(AndroidJUnit4::class)
class ScreenUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun str(@StringRes id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val classes = listOf(
        TimetableClass("1", "Job English", DayOfWeek.MONDAY, LocalTime.of(12, 0), room = "탐구관307"),
        TimetableClass("2", "실용영어청취", DayOfWeek.MONDAY, LocalTime.of(13, 30)),
        TimetableClass("3", "AI와 UXD", DayOfWeek.FRIDAY, LocalTime.of(9, 0)),
    )

    private fun timetable(
        state: TimetableUiState,
        onEdit: (TimetableClass) -> Unit = {},
        onClearAll: () -> Unit = {},
        onCreateAlarms: () -> Unit = {},
    ) = compose.setContent {
        RouteAlarmTheme {
            TimetableScreen(
                state = state, onBack = {}, onPickImage = {}, onOpenCalendar = {}, onAdd = {},
                onEdit = onEdit, onClearAll = onClearAll, onCreateAlarms = onCreateAlarms,
            )
        }
    }

    @Test
    fun timetable_empty_showsGuideAndNoAlarmButton() {
        timetable(TimetableUiState())

        compose.onNodeWithText(str(R.string.timetable_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.timetable_create_alarms, 1)).assertDoesNotExist()
    }

    @Test
    fun timetable_showsClassesByDay_andCreatesOneAlarmPerFirstClass() {
        var created = 0
        var edited: TimetableClass? = null
        timetable(TimetableUiState(classes = classes, proposals = TimetablePolicy.proposals(classes)), onEdit = { edited = it }, onCreateAlarms = { created++ })

        compose.onNodeWithText(str(R.string.timetable_day_format, "월")).assertIsDisplayed()
        compose.onNodeWithText("탐구관307").assertIsDisplayed()
        // 월(12:00)·금(09:00) 첫 수업 → 알람 2개
        compose.onNodeWithText(str(R.string.timetable_create_alarms, 2)).performClick()
        compose.onNodeWithText("Job English").performClick()

        assertEquals(1, created)
        assertEquals("1", edited?.id)
    }

    @Test
    fun timetable_clearAll_asksBeforeDeleting() {
        var cleared = 0
        timetable(TimetableUiState(classes = classes, proposals = TimetablePolicy.proposals(classes)), onClearAll = { cleared++ })

        compose.onNodeWithText(str(R.string.timetable_clear_all)).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.timetable_clear_confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.common_cancel)).performClick()
        assertEquals(0, cleared)

        compose.onNodeWithText(str(R.string.timetable_clear_all)).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.common_delete)).performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun calendar_needsPermission_requestsIt() {
        var requested = 0
        compose.setContent {
            RouteAlarmTheme {
                CalendarImportScreen(CalendarUiState.NeedsPermission, onBack = {}, onRequestPermission = { requested++ }, onRetry = {}, onAddWeekly = {}, onPickEvent = {})
            }
        }

        compose.onNodeWithText(str(R.string.calendar_permission_allow)).performClick()

        assertEquals(1, requested)
    }

    @Test
    fun calendar_loaded_addsWeeklyAndPicksUpcomingEvent() {
        val weekly = CalendarEvent(1, "모바일프로그래밍", Instant.parse("2026-12-01T01:30:00Z"), "한성대학교 상상관", setOf(DayOfWeek.TUESDAY))
        val exam = CalendarEvent(2, "토익 시험", Instant.parse("2026-11-29T00:20:00Z"), "한성대학교", emptySet())
        var added = 0
        var picked: CalendarEvent? = null
        compose.setContent {
            RouteAlarmTheme {
                CalendarImportScreen(
                    CalendarUiState.Loaded(weekly = listOf(weekly), upcoming = listOf(exam)),
                    onBack = {}, onRequestPermission = {}, onRetry = {}, onAddWeekly = { added++ }, onPickEvent = { picked = it },
                )
            }
        }

        compose.onNodeWithText("모바일프로그래밍").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.calendar_add_weekly, 1)).performClick()
        compose.onNodeWithText("토익 시험").performClick()

        assertEquals(1, added)
        assertEquals(exam, picked)
    }

    @Test
    fun pendingAdjustment_showsChangeAndCallsApplyOrKeep() {
        var applied = 0
        var kept = 0
        compose.setContent {
            RouteAlarmTheme {
                PendingAdjustmentCard(
                    kind = AdjustmentKind.SUGGESTED, deltaMinutes = -8, from = "07:15", to = "07:07",
                    onApply = { applied++ }, onDismiss = { kept++ },
                )
            }
        }

        compose.onNodeWithText("07:07").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.home_pending_apply)).performClick()
        compose.onNodeWithText(str(R.string.home_pending_dismiss)).performClick()

        assertTrue(applied == 1 && kept == 1)
    }
}
