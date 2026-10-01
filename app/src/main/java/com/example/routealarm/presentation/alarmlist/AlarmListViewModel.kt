package com.example.routealarm.presentation.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.usecase.DeleteScheduleUseCase
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import com.example.routealarm.domain.usecase.SetScheduleEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlarmListUiState {
    data object Loading : AlarmListUiState
    data object Empty : AlarmListUiState
    data class Success(val schedules: List<Schedule>) : AlarmListUiState
}

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    scheduleRepository: ScheduleRepository,
    private val setScheduleEnabled: SetScheduleEnabledUseCase,
    private val deleteSchedule: DeleteScheduleUseCase,
    private val refreshTransitTime: RefreshTransitTimeUseCase,
) : ViewModel() {

    val uiState: StateFlow<AlarmListUiState> = scheduleRepository.observeSchedules()
        .map { list -> if (list.isEmpty()) AlarmListUiState.Empty else AlarmListUiState.Success(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AlarmListUiState.Loading)

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    fun toggle(scheduleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            when (setScheduleEnabled(scheduleId, enabled)) {
                is AppResult.Success -> if (enabled) refreshTransitTime(scheduleId)
                is AppResult.Failure -> _error.value = AppError.Database
            }
        }
    }

    fun delete(scheduleId: Long) {
        viewModelScope.launch {
            val result = deleteSchedule(scheduleId)
            if (result is AppResult.Failure) _error.value = result.error
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
