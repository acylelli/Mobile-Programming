package com.example.routealarm.alarm.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlarmRingingUiState(
    val schedule: Schedule? = null,
    val snoozeMinutes: Int = 5,
)

@HiltViewModel
class AlarmRingingViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmRingingUiState())
    val uiState: StateFlow<AlarmRingingUiState> = _uiState.asStateFlow()

    fun load(scheduleId: Long) {
        viewModelScope.launch {
            _uiState.value = AlarmRingingUiState(
                schedule = scheduleRepository.getSchedule(scheduleId),
                snoozeMinutes = preferencesRepository.current().snoozeMinutes,
            )
        }
    }
}
