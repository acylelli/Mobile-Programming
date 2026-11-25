package com.example.routealarm.presentation.schedule.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.routealarm.R
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.ScheduleSound
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.policy.WakeUpHabit
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.usecase.ApplyPendingAdjustmentUseCase
import com.example.routealarm.domain.usecase.ApplyWakeUpHabitUseCase
import com.example.routealarm.domain.usecase.DeleteScheduleUseCase
import com.example.routealarm.domain.usecase.DismissPendingAdjustmentUseCase
import com.example.routealarm.domain.usecase.DismissWakeUpHabitUseCase
import com.example.routealarm.domain.usecase.ObserveWakeUpHabitUseCase
import com.example.routealarm.domain.usecase.RefreshOutcome
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import com.example.routealarm.domain.usecase.SetScheduleEnabledUseCase
import com.example.routealarm.domain.usecase.SetScheduleSoundUseCase
import com.example.routealarm.domain.usecase.SetSkipHolidaysUseCase
import com.example.routealarm.presentation.common.UndoManager
import com.example.routealarm.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScheduleDetailUiState {
    data object Loading : ScheduleDetailUiState
    data object NotFound : ScheduleDetailUiState
    data class Success(
        val schedule: Schedule,
        val history: List<AlarmHistoryEvent>,
        val isRefreshing: Boolean,
        val refreshError: AppError?,
        val deleted: Boolean,
        /** 기상 습관 제안(평소 늦게 일어나면 준비 시간 늘리기). 없으면 null */
        val wakeUpHabit: WakeUpHabit? = null,
        /** 일정 알람음이 "설정 따르기"일 때 보여 줄 설정 값 */
        val preferences: UserPreferences = UserPreferences(),
    ) : ScheduleDetailUiState
}

@HiltViewModel
class ScheduleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scheduleRepository: ScheduleRepository,
    historyRepository: AlarmHistoryRepository,
    private val refreshTransitTime: RefreshTransitTimeUseCase,
    private val setScheduleEnabled: SetScheduleEnabledUseCase,
    private val deleteSchedule: DeleteScheduleUseCase,
    private val applyPendingAdjustment: ApplyPendingAdjustmentUseCase,
    private val dismissPendingAdjustment: DismissPendingAdjustmentUseCase,
    observeWakeUpHabit: ObserveWakeUpHabitUseCase,
    private val applyWakeUpHabit: ApplyWakeUpHabitUseCase,
    private val dismissWakeUpHabit: DismissWakeUpHabitUseCase,
    private val undoManager: UndoManager,
    private val setSkipHolidaysUseCase: SetSkipHolidaysUseCase,
    private val setScheduleSound: SetScheduleSoundUseCase,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val scheduleId: Long = savedStateHandle.toRoute<Route.ScheduleDetail>().scheduleId

    private data class LocalState(val refreshing: Boolean = false, val error: AppError? = null, val deleted: Boolean = false)

    private val local = MutableStateFlow(LocalState())
    private val _message = MutableStateFlow<AppError?>(null)
    val message: StateFlow<AppError?> = _message.asStateFlow()

    val uiState: StateFlow<ScheduleDetailUiState> = combine(
        scheduleRepository.observeSchedule(scheduleId),
        historyRepository.observeHistory(scheduleId),
        observeWakeUpHabit(scheduleId),
        local,
        preferencesRepository.preferences,
    ) { schedule, history, habit, state, prefs ->
        when {
            state.deleted -> ScheduleDetailUiState.Success(schedule ?: return@combine ScheduleDetailUiState.NotFound, history, false, null, deleted = true)
            schedule == null -> ScheduleDetailUiState.NotFound
            else -> ScheduleDetailUiState.Success(
                schedule, history, state.refreshing, state.error, deleted = false, wakeUpHabit = habit, preferences = prefs,
            )
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
            val snapshot = scheduleRepository.getSchedule(scheduleId)
            when (val result = deleteSchedule(scheduleId)) {
                is AppResult.Success -> {
                    local.update { it.copy(deleted = true) }
                    snapshot?.let { undoManager.offer(R.string.undo_deleted, it) }
                }
                is AppResult.Failure -> _message.value = result.error
            }
        }
    }

    fun applyPending() {
        viewModelScope.launch {
            val snapshot = scheduleRepository.getSchedule(scheduleId)
            when (val result = applyPendingAdjustment(scheduleId)) {
                is AppResult.Failure -> _message.value = result.error
                // 변경안이 낡아 다시 계산만 요청한 경우(false)는 바뀐 것이 없다.
                is AppResult.Success -> if (result.data) snapshot?.let { undoManager.offer(R.string.undo_changed, it) }
            }
        }
    }

    fun dismissPending() {
        viewModelScope.launch { dismissPendingAdjustment(scheduleId) }
    }

    fun applyHabit(extraMinutes: Int) {
        viewModelScope.launch {
            val snapshot = scheduleRepository.getSchedule(scheduleId)
            when (val result = applyWakeUpHabit(scheduleId, extraMinutes)) {
                is AppResult.Failure -> _message.value = result.error
                is AppResult.Success -> snapshot?.let { undoManager.offer(R.string.undo_changed, it) }
            }
        }
    }

    fun dismissHabit() {
        viewModelScope.launch { dismissWakeUpHabit(scheduleId) }
    }

    fun setSkipHolidays(skip: Boolean) {
        viewModelScope.launch {
            val result = setSkipHolidaysUseCase(scheduleId, skip)
            if (result is AppResult.Failure) _message.value = result.error
        }
    }

    /** null 이면 설정 따르기 */
    fun setSound(sound: ScheduleSound?) {
        viewModelScope.launch {
            val result = setScheduleSound(scheduleId, sound)
            if (result is AppResult.Failure) _message.value = result.error
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
