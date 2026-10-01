package com.example.routealarm.presentation.schedule.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.usecase.ApplyPendingAdjustmentUseCase
import com.example.routealarm.domain.usecase.DeleteScheduleUseCase
import com.example.routealarm.domain.usecase.DismissPendingAdjustmentUseCase
import com.example.routealarm.domain.usecase.RefreshOutcome
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import com.example.routealarm.domain.usecase.SetScheduleEnabledUseCase
import com.example.routealarm.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScheduleDetailUiState {
    data object Loading : ScheduleDetailUiState
    data object NotFound : ScheduleDetailUiState
    data class Success(
        val schedule: Schedule,
        val history: List<AlarmHistoryEvent>,
        val isRefreshing: Boolean,
        val refreshError: AppError?,
        val deleted: Boolean,
    ) : ScheduleDetailUiState
}

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    scheduleRepository: ScheduleRepository,
    historyRepository: AlarmHistoryRepository,
    private val refreshTransitTime: RefreshTransitTimeUseCase,
    private val setScheduleEnabled: SetScheduleEnabledUseCase,
    private val deleteSchedule: DeleteScheduleUseCase,
    private val applyPendingAdjustment: ApplyPendingAdjustmentUseCase,
    private val dismissPendingAdjustment: DismissPendingAdjustmentUseCase,
) : ViewModel() {

    private val scheduleId: Long = savedStateHandle.toRoute<Route.ScheduleDetail>().scheduleId

    private data class LocalState(val refreshing: Boolean = false, val error: AppError? = null, val deleted: Boolean = false)

    private val local = MutableStateFlow(LocalState())
    private val _message = MutableStateFlow<AppError?>(null)
    val message: StateFlow<AppError?> = _message.asStateFlow()

    val uiState: StateFlow<ScheduleDetailUiState> = combine(
        scheduleRepository.observeSchedule(scheduleId),
        historyRepository.observeHistory(scheduleId),
        local,
    ) { schedule, history, state ->
        when {
            state.deleted -> ScheduleDetailUiState.Success(schedule ?: return@combine ScheduleDetailUiState.NotFound, history, false, null, deleted = true)
            schedule == null -> ScheduleDetailUiState.NotFound
            else -> ScheduleDetailUiState.Success(schedule, history, state.refreshing, state.error, deleted = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleDetailUiState.Loading)

    fun refresh() {
        if (local.value.refreshing) return
        viewModelScope.launch {
            local.update { it.copy(refreshing = true, error = null) }
            val outcome = refreshTransitTime(scheduleId)
            local.update { it.copy(refreshing = false, error = (outcome as? RefreshOutcome.Failed)?.error) }
        }
    }

    fun toggle(enabled: Boolean) {
        viewModelScope.launch {
            val result = setScheduleEnabled(scheduleId, enabled)
            if (result is AppResult.Failure) _message.value = result.error else if (enabled) refresh()
        }
    }

    fun delete() {
        viewModelScope.launch {
            when (val result = deleteSchedule(scheduleId)) {
                is AppResult.Success -> local.update { it.copy(deleted = true) }
                is AppResult.Failure -> _message.value = result.error
            }
        }
    }

    fun applyPending() {
        viewModelScope.launch {
            val result = applyPendingAdjustment(scheduleId)
            if (result is AppResult.Failure) _message.value = result.error
        }
    }

    fun dismissPending() {
        viewModelScope.launch { dismissPendingAdjustment(scheduleId) }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
