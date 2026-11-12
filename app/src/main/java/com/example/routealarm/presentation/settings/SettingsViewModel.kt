package com.example.routealarm.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.BuildConfig
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.MetricsSummary
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.repository.MetricsRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.SubscriptionRepository
import com.example.routealarm.domain.usecase.RefreshTransitTimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val entitlement: Entitlement = Entitlement.Free,
    /** PRO 배너와 잠금 표시를 결정한다. */
    val isPro: Boolean = false,
    val metrics: MetricsSummary = MetricsSummary(),
    val transitProvider: String = BuildConfig.TRANSIT_PROVIDER,
    val versionName: String = BuildConfig.VERSION_NAME,
    val isRefreshingAll: Boolean = false,
)

sealed interface SettingsEvent {
    data object RefreshDone : SettingsEvent

    /** 시연: 이 일정의 기상 알람을 지금 울린다. */
    data class RingAlarm(val scheduleId: Long) : SettingsEvent
    data object NoScheduleToRing : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    subscriptionRepository: SubscriptionRepository,
    metricsRepository: MetricsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val refreshTransitTime: RefreshTransitTimeUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val refreshingAll = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        subscriptionRepository.entitlement,
        metricsRepository.observeSummary(),
        refreshingAll,
    ) { prefs, entitlement, metrics, refreshing ->
        SettingsUiState(
            preferences = prefs,
            entitlement = entitlement,
            isPro = entitlement.isPro(timeProvider.now()),
            metrics = metrics,
            isRefreshingAll = refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    fun setDefaultPreparation(minutes: Int) = launch { preferencesRepository.setDefaultPreparationMinutes(minutes) }
    fun setDefaultBuffer(minutes: Int) = launch { preferencesRepository.setDefaultBufferMinutes(minutes) }
    fun setAutoAdjustment(enabled: Boolean) = launch { preferencesRepository.setAutoAdjustment(enabled) }
    fun setTrafficNotifications(enabled: Boolean) = launch { preferencesRepository.setTrafficNotifications(enabled) }
    fun setVibration(enabled: Boolean) = launch { preferencesRepository.setVibration(enabled) }
    fun setAlarmSound(sound: AlarmSound) = launch { preferencesRepository.setAlarmSound(sound) }
    fun setSnoozeMinutes(minutes: Int) = launch { preferencesRepository.setSnoozeMinutes(minutes) }
    fun setDemoExtraDelay(minutes: Int) = launch { preferencesRepository.setDemoExtraDelay(minutes) }
    fun setDemoNetworkFailure(enabled: Boolean) = launch { preferencesRepository.setDemoNetworkFailure(enabled) }

    /** 데모: 모든 활성 일정의 교통 정보를 지금 다시 확인한다(WorkManager 체크포인트를 기다리지 않음). */
    fun refreshAllNow() {
        if (refreshingAll.value) return
        viewModelScope.launch {
            refreshingAll.value = true
            scheduleRepository.getEnabledSchedules().forEach { refreshTransitTime(it.id) }
            refreshingAll.value = false
            _events.send(SettingsEvent.RefreshDone)
        }
    }

    /** 데모: 알람 시각을 기다리지 않고 기상 알람 화면과 알람음을 확인한다. 켜진 일정을 우선한다. */
    fun ringAlarmNow() {
        viewModelScope.launch {
            val schedule = scheduleRepository.observeSchedules().first()
                .filter { it.plan != null }
                .sortedByDescending { it.enabled }
                .firstOrNull()
            _events.send(if (schedule != null) SettingsEvent.RingAlarm(schedule.id) else SettingsEvent.NoScheduleToRing)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
