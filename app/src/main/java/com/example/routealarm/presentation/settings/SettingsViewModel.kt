package com.example.routealarm.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.BuildConfig
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.MetricsSummary
import com.example.routealarm.domain.model.ThemeMode
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.repository.MetricsRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val metrics: MetricsSummary = MetricsSummary(),
    val transitProvider: String = BuildConfig.TRANSIT_PROVIDER,
    val versionName: String = BuildConfig.VERSION_NAME,
    val isRefreshingAll: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    metricsRepository: MetricsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val refreshTransitTime: RefreshTransitTimeUseCase,
) : ViewModel() {

    private val refreshingAll = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        metricsRepository.observeSummary(),
        refreshingAll,
    ) { prefs, metrics, refreshing ->
        SettingsUiState(preferences = prefs, metrics = metrics, isRefreshingAll = refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    private val _refreshDone = MutableStateFlow(false)
    val refreshDone: StateFlow<Boolean> = _refreshDone.asStateFlow()

    fun setDefaultPreparation(minutes: Int) = launch { preferencesRepository.setDefaultPreparationMinutes(minutes) }
    fun setDefaultBuffer(minutes: Int) = launch { preferencesRepository.setDefaultBufferMinutes(minutes) }
    fun setAutoAdjustment(enabled: Boolean) = launch { preferencesRepository.setAutoAdjustment(enabled) }
    fun setTrafficNotifications(enabled: Boolean) = launch { preferencesRepository.setTrafficNotifications(enabled) }
    fun setVibration(enabled: Boolean) = launch { preferencesRepository.setVibration(enabled) }
    fun setAlarmSound(sound: AlarmSound) = launch { preferencesRepository.setAlarmSound(sound) }
    fun setSnoozeMinutes(minutes: Int) = launch { preferencesRepository.setSnoozeMinutes(minutes) }
    fun setThemeMode(mode: ThemeMode) = launch { preferencesRepository.setThemeMode(mode) }
    fun setDemoExtraDelay(minutes: Int) = launch { preferencesRepository.setDemoExtraDelay(minutes) }
    fun setDemoNetworkFailure(enabled: Boolean) = launch { preferencesRepository.setDemoNetworkFailure(enabled) }

    /** 데모: 모든 활성 일정의 교통 정보를 지금 다시 확인한다(WorkManager 체크포인트를 기다리지 않음). */
    fun refreshAllNow() {
        if (refreshingAll.value) return
        viewModelScope.launch {
            refreshingAll.value = true
            scheduleRepository.getEnabledSchedules().forEach { refreshTransitTime(it.id) }
            refreshingAll.value = false
            _refreshDone.value = true
        }
    }

    fun consumeRefreshDone() {
        _refreshDone.value = false
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
