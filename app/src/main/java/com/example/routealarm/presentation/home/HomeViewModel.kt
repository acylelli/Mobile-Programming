package com.example.routealarm.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.usecase.ApplyPendingAdjustmentUseCase
import com.example.routealarm.domain.usecase.DismissPendingAdjustmentUseCase
import com.example.routealarm.domain.usecase.RefreshOutcome
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import com.example.routealarm.domain.usecase.SeedDemoDataUseCase
import com.example.routealarm.domain.usecase.SetScheduleEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Success(
        /** 가장 가까운 기상 알람. 활성 일정이 없으면 비활성 일정 중 첫 번째 */
        val primary: Schedule,
        val upcoming: List<Schedule>,
        val isRefreshing: Boolean,
        val isStale: Boolean,
        /** 마지막 갱신 실패 메시지(오프라인 배너) */
        val refreshError: AppError?,
        val pendingOutdatedMessage: Boolean,
    ) : HomeUiState
}

sealed interface HomeEvent {
    data class ShowMessage(val error: AppError) : HomeEvent
}

/**
 * 홈 화면. 일정 목록을 Room Flow 로 구독하므로 백그라운드 Worker 가 알람을 바꾸면 화면도 즉시 갱신된다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    scheduleRepository: ScheduleRepository,
    private val refreshTransitTime: RefreshTransitTimeUseCase,
    private val setScheduleEnabled: SetScheduleEnabledUseCase,
    private val applyPendingAdjustment: ApplyPendingAdjustmentUseCase,
    private val dismissPendingAdjustment: DismissPendingAdjustmentUseCase,
    private val seedDemoData: SeedDemoDataUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private data class LocalState(
        val refreshingIds: Set<Long> = emptySet(),
        val refreshError: AppError? = null,
        val pendingOutdated: Boolean = false,
        val seeded: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState())
    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(scheduleRepository.observeSchedules(), local) { schedules, state ->
        if (schedules.isEmpty()) {
            if (state.seeded) HomeUiState.Empty else HomeUiState.Loading
        } else {
            val now = timeProvider.now()
            val sorted = schedules.sortedWith(
                compareByDescending<Schedule> { it.enabled }
                    .thenBy { it.plan?.wakeUp ?: java.time.Instant.MAX },
            )
            val primary = sorted.first()
            HomeUiState.Success(
                primary = primary,
                upcoming = sorted.drop(1),
                isRefreshing = primary.id in state.refreshingIds,
                isStale = primary.plan?.let { !it.isRealtime && it.isStale(now, STALE_THRESHOLD) } ?: false,
                refreshError = state.refreshError,
                pendingOutdatedMessage = state.pendingOutdated,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState.Loading)

    init {
        viewModelScope.launch {
            seedDemoData()
            local.update { it.copy(seeded = true) }
        }
    }

    fun refresh(scheduleId: Long) {
        if (scheduleId in local.value.refreshingIds) return
        viewModelScope.launch {
            local.update { it.copy(refreshingIds = it.refreshingIds + scheduleId, refreshError = null, pendingOutdated = false) }
            val outcome = refreshTransitTime(scheduleId)
            local.update {
                it.copy(
                    refreshingIds = it.refreshingIds - scheduleId,
                    refreshError = (outcome as? RefreshOutcome.Failed)?.error,
                )
            }
        }
    }

    fun toggle(scheduleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val result = setScheduleEnabled(scheduleId, enabled)
            if (result is AppResult.Failure) _events.value = HomeEvent.ShowMessage(result.error)
            else if (enabled) refresh(scheduleId)
        }
    }

    fun applyPending(scheduleId: Long) {
        viewModelScope.launch {
            when (val result = applyPendingAdjustment(scheduleId)) {
                is AppResult.Success -> if (!result.data) local.update { it.copy(pendingOutdated = true) }
                is AppResult.Failure -> _events.value = HomeEvent.ShowMessage(result.error)
            }
        }
    }

    fun dismissPending(scheduleId: Long) {
        viewModelScope.launch { dismissPendingAdjustment(scheduleId) }
    }

    fun consumeEvent() {
        _events.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val STALE_THRESHOLD: Duration = Duration.ofHours(6)
    }
}
