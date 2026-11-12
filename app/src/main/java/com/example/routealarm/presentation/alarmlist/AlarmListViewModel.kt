package com.example.routealarm.presentation.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.ScheduleQuota
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.SubscriptionRepository
import com.example.routealarm.domain.usecase.CheckScheduleQuotaUseCase
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import com.example.routealarm.domain.usecase.SetScheduleEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlarmListItem(
    val schedule: Schedule,
    /** 지금 등록된 기상 알람이 교통 변화로 옮겨진 것이라면 그 조정 기록(변경 전 → 변경 후 표시용) */
    val adjustment: AlarmHistoryEvent?,
)

sealed interface AlarmListUiState {
    data object Loading : AlarmListUiState
    data class Content(val items: List<AlarmListItem>, val quota: ScheduleQuota) : AlarmListUiState
}

sealed interface AlarmListEvent {
    data object OpenCreate : AlarmListEvent

    /** 무료 플랜 한도에 걸려 PRO 페이월을 띄운다(UI-D-001 ①). */
    data object ShowPaywall : AlarmListEvent

    /** 오늘 이미 페이월을 보여줬다면 반복해서 띄우지 않고 짧게 안내만 한다(SFR-015). */
    data class ShowLimitMessage(val limit: Int) : AlarmListEvent
    data class ShowError(val error: AppError) : AlarmListEvent
}

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    scheduleRepository: ScheduleRepository,
    historyRepository: AlarmHistoryRepository,
    subscriptionRepository: SubscriptionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val checkScheduleQuota: CheckScheduleQuotaUseCase,
    private val setScheduleEnabled: SetScheduleEnabledUseCase,
    private val refreshTransitTime: RefreshTransitTimeUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<AlarmListUiState> = combine(
        scheduleRepository.observeSchedules(),
        historyRepository.observeLatestAdjustments(),
        subscriptionRepository.entitlement,
    ) { schedules, adjustments, entitlement ->
        AlarmListUiState.Content(
            items = schedules.map { schedule ->
                // 조정 기록의 새 시각이 현재 알람과 같을 때만 "이번 알람이 옮겨졌다"고 볼 수 있다.
                val adjustment = adjustments[schedule.id]?.takeIf { event ->
                    event.previousWakeUp != null && event.newWakeUp != null &&
                        event.newWakeUp == schedule.plan?.wakeUp && event.previousWakeUp != event.newWakeUp
                }
                AlarmListItem(schedule, adjustment)
            },
            quota = checkScheduleQuota.evaluate(schedules, entitlement, timeProvider.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AlarmListUiState.Loading)

    private val _events = Channel<AlarmListEvent>(Channel.BUFFERED)
    val events: Flow<AlarmListEvent> = _events.receiveAsFlow()

    /** 새 일정(+). 무료 플랜에서 일정이 한도에 차 있으면 만들기 대신 PRO 페이월을 띄운다. */
    fun onAddClicked() {
        viewModelScope.launch {
            val quota = checkScheduleQuota()
            if (quota !is ScheduleQuota.Limited || !quota.reached) {
                _events.send(AlarmListEvent.OpenCreate)
                return@launch
            }
            val today = timeProvider.nowZoned().toLocalDate().toEpochDay()
            if (preferencesRepository.current().lastPaywallPromptEpochDay == today) {
                _events.send(AlarmListEvent.ShowLimitMessage(quota.limit))
            } else {
                preferencesRepository.setLastPaywallPromptDay(today)
                _events.send(AlarmListEvent.ShowPaywall)
            }
        }
    }

    fun toggle(scheduleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            when (val result = setScheduleEnabled(scheduleId, enabled)) {
                is AppResult.Success -> if (enabled) refreshTransitTime(scheduleId)
                is AppResult.Failure -> _events.send(AlarmListEvent.ShowError(result.error))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
