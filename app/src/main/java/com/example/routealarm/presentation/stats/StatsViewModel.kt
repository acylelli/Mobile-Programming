package com.example.routealarm.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.policy.AlarmStats
import com.example.routealarm.domain.policy.AlarmStatsPolicy
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 기록·통계 화면 상태. 불러오는 중이면 stats 가 null */
data class StatsUiState(val stats: AlarmStats? = null)

@HiltViewModel
class StatsViewModel @Inject constructor(
    historyRepository: AlarmHistoryRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = historyRepository
        .observeSince(timeProvider.now().minus(AlarmStatsPolicy.LOOKBACK))
        .map { events -> StatsUiState(AlarmStatsPolicy.summarize(events, timeProvider.now(), timeProvider.zone())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatsUiState())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
