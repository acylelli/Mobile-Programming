package com.example.routealarm.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.TimetableClass
import com.example.routealarm.domain.repository.CalendarEvent
import com.example.routealarm.domain.repository.CalendarRepository
import com.example.routealarm.domain.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface CalendarUiState {
    data object NeedsPermission : CalendarUiState
    data object Loading : CalendarUiState
    data class Loaded(
        /** 매주 반복되는 일정(수업 등) — 시간표로 가져올 수 있다 */
        val weekly: List<CalendarEvent>,
        /** 한 번뿐인 일정 — 눌러서 그 일정에 맞춘 알람을 만든다 */
        val upcoming: List<CalendarEvent>,
    ) : CalendarUiState
    data class Failed(val error: AppError) : CalendarUiState
}

sealed interface CalendarImportEvent {
    data class AddedToTimetable(val count: Int) : CalendarImportEvent
}

/**
 * 캘린더 연동: 기기 캘린더에서 앞으로 2주 일정을 읽는다.
 * - 한 번뿐인 일정은 도착지·도착 시각·날짜를 채운 "새 일정 만들기"로 넘긴다.
 * - 매주 반복 일정(수업)은 시간표로 가져와 요일별 첫 수업 알람을 만들 수 있게 한다.
 */
@HiltViewModel
class CalendarImportViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val timetableRepository: TimetableRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CalendarUiState>(
        if (calendarRepository.hasPermission()) CalendarUiState.Loading else CalendarUiState.NeedsPermission,
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _events = Channel<CalendarImportEvent>(Channel.BUFFERED)
    val events: Flow<CalendarImportEvent> = _events.receiveAsFlow()

    init {
        if (calendarRepository.hasPermission()) load()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) load() else _uiState.value = CalendarUiState.NeedsPermission
    }

    fun load() {
        _uiState.value = CalendarUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = calendarRepository.upcomingEvents(LOOKAHEAD_DAYS)) {
                is AppResult.Success -> {
                    val (weekly, once) = result.data.partition { it.weeklyDays.isNotEmpty() }
                    CalendarUiState.Loaded(weekly = weekly, upcoming = once)
                }
                is AppResult.Failure -> if (result.error == AppError.CalendarPermissionDenied) {
                    CalendarUiState.NeedsPermission
                } else {
                    CalendarUiState.Failed(result.error)
                }
            }
        }
    }

    /** 매주 반복 일정을 요일마다 수업 하나로 바꿔 시간표에 더한다. */
    fun addWeeklyToTimetable() {
        val state = _uiState.value as? CalendarUiState.Loaded ?: return
        val zone = timeProvider.zone()
        val classes = state.weekly.flatMap { event ->
            val start = event.begin.atZone(zone).toLocalTime().withSecond(0).withNano(0)
            event.weeklyDays.map { day ->
                TimetableClass(UUID.randomUUID().toString(), event.title.ifBlank { "수업" }, day, start, room = event.location)
            }
        }
        if (classes.isEmpty()) return
        viewModelScope.launch {
            timetableRepository.addAll(classes)
            _events.send(CalendarImportEvent.AddedToTimetable(classes.size))
        }
    }


    private companion object {
        const val LOOKAHEAD_DAYS = 14L
    }
}
