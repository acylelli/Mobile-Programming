package com.example.routealarm.presentation.schedule.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.repository.LocationRepository
import com.example.routealarm.domain.repository.PlaceRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.usecase.PreviewScheduleUseCase
import com.example.routealarm.domain.usecase.SaveScheduleUseCase
import com.example.routealarm.domain.usecase.ScheduleDraft
import com.example.routealarm.domain.usecase.SchedulePreview
import com.example.routealarm.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

enum class CreateStep { ORIGIN, DESTINATION, TIME, PREPARATION, BUFFER, RESULT }

enum class BufferPreset(val minutes: Int) { TIGHT(5), NORMAL(10), RELAXED(20) }

enum class RepeatPreset { ONCE, WEEKDAYS, DAILY, CUSTOM }

sealed interface ResultState {
    data object Idle : ResultState
    data object Calculating : ResultState
    data class Ready(val preview: SchedulePreview, val inexactWarning: Boolean = false) : ResultState
    data class Saved(val scheduleId: Long, val inexactWarning: Boolean) : ResultState
    data class Failed(val error: AppError) : ResultState
}

data class CreateScheduleUiState(
    val step: CreateStep = CreateStep.ORIGIN,
    val isEditing: Boolean = false,
    val title: String = "",
    val origin: Place? = null,
    val destination: Place? = null,
    val useCurrentLocation: Boolean = false,
    val targetTime: LocalTime = LocalTime.of(9, 0),
    val repeatPreset: RepeatPreset = RepeatPreset.WEEKDAYS,
    val customDays: Set<DayOfWeek> = WEEKDAYS,
    val preparationMinutes: Int = 30,
    val bufferPreset: BufferPreset? = BufferPreset.NORMAL,
    val customBufferMinutes: Int = 10,
    val alarmMode: AlarmMode = AlarmMode.WAKE_UP_AND_DEPARTURE,
    val savedPlaces: List<Place> = emptyList(),
    val hasLocationPermission: Boolean = false,
    val result: ResultState = ResultState.Idle,
    val validationError: AppError? = null,
) {
    val repeatDays: Set<DayOfWeek>
        get() = when (repeatPreset) {
            RepeatPreset.ONCE -> emptySet()
            RepeatPreset.WEEKDAYS -> WEEKDAYS
            RepeatPreset.DAILY -> DayOfWeek.entries.toSet()
            RepeatPreset.CUSTOM -> customDays
        }
    val bufferMinutes: Int get() = bufferPreset?.minutes ?: customBufferMinutes
    val stepIndex: Int get() = CreateStep.entries.indexOf(step)
    val stepCount: Int get() = CreateStep.entries.size - 1 // RESULT 는 단계 카운트에서 제외

    companion object {
        val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}

/**
 * 단계별 일정 생성. 모든 입력은 ViewModel 에 호이스팅되고 화면은 Stateless 다.
 * 계산(Preview)과 저장(Save)을 분리해 사용자가 결과를 보고 결정할 수 있게 한다.
 */
@HiltViewModel
class CreateScheduleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val previewSchedule: PreviewScheduleUseCase,
    private val saveSchedule: SaveScheduleUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val locationRepository: LocationRepository,
    placeRepository: PlaceRepository,
) : ViewModel() {

    private val editScheduleId: Long? = savedStateHandle.toRoute<Route.CreateSchedule>().editScheduleId

    private val _uiState = MutableStateFlow(CreateScheduleUiState(isEditing = editScheduleId != null))
    val uiState: StateFlow<CreateScheduleUiState> = _uiState.asStateFlow()

    val savedPlaces: StateFlow<List<Place>> = placeRepository.observeSavedPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.current()
            val existing = editScheduleId?.let { scheduleRepository.getSchedule(it) }
            _uiState.update { state ->
                if (existing != null) {
                    val preset = when (existing.bufferMinutes) {
                        BufferPreset.TIGHT.minutes -> BufferPreset.TIGHT
                        BufferPreset.NORMAL.minutes -> BufferPreset.NORMAL
                        BufferPreset.RELAXED.minutes -> BufferPreset.RELAXED
                        else -> null
                    }
                    state.copy(
                        title = existing.title,
                        origin = existing.origin,
                        destination = existing.destination,
                        useCurrentLocation = existing.useCurrentLocation,
                        targetTime = existing.targetArrivalTime,
                        repeatPreset = when (existing.repeatDays) {
                            emptySet<DayOfWeek>() -> RepeatPreset.ONCE
                            CreateScheduleUiState.WEEKDAYS -> RepeatPreset.WEEKDAYS
                            DayOfWeek.entries.toSet() -> RepeatPreset.DAILY
                            else -> RepeatPreset.CUSTOM
                        },
                        customDays = existing.repeatDays.ifEmpty { CreateScheduleUiState.WEEKDAYS },
                        preparationMinutes = existing.preparationMinutes,
                        bufferPreset = preset,
                        customBufferMinutes = existing.bufferMinutes,
                        alarmMode = existing.alarmMode,
                        hasLocationPermission = locationRepository.hasLocationPermission(),
                    )
                } else {
                    state.copy(
                        preparationMinutes = prefs.defaultPreparationMinutes,
                        bufferPreset = BufferPreset.entries.firstOrNull { it.minutes == prefs.defaultBufferMinutes },
                        customBufferMinutes = prefs.defaultBufferMinutes,
                        hasLocationPermission = locationRepository.hasLocationPermission(),
                    )
                }
            }
        }
    }

    fun setOrigin(place: Place) = _uiState.update { it.copy(origin = place, useCurrentLocation = false, validationError = null) }
    fun setDestination(place: Place) = _uiState.update { it.copy(destination = place, validationError = null) }

    fun useCurrentLocation(placeholderName: String) = _uiState.update {
        it.copy(
            useCurrentLocation = true,
            origin = Place(name = placeholderName, address = "", latitude = 0.0, longitude = 0.0),
            hasLocationPermission = locationRepository.hasLocationPermission(),
            validationError = null,
        )
    }

    fun setTitle(title: String) = _uiState.update { it.copy(title = title) }
    fun setTargetTime(time: LocalTime) = _uiState.update { it.copy(targetTime = time) }
    fun setRepeatPreset(preset: RepeatPreset) = _uiState.update { it.copy(repeatPreset = preset) }
    fun toggleCustomDay(day: DayOfWeek) = _uiState.update {
        val days = if (day in it.customDays) it.customDays - day else it.customDays + day
        it.copy(customDays = days, repeatPreset = RepeatPreset.CUSTOM)
    }

    fun setPreparation(minutes: Int) = _uiState.update { it.copy(preparationMinutes = minutes) }
    fun setBufferPreset(preset: BufferPreset?) = _uiState.update { it.copy(bufferPreset = preset) }
    fun setCustomBuffer(minutes: Int) = _uiState.update { it.copy(customBufferMinutes = minutes, bufferPreset = null) }
    fun setAlarmMode(mode: AlarmMode) = _uiState.update { it.copy(alarmMode = mode) }

    fun refreshPermission() = _uiState.update { it.copy(hasLocationPermission = locationRepository.hasLocationPermission()) }

    /** 다음 단계로. 현재 단계 입력을 검증하고 마지막 단계면 계산을 시작한다. */
    fun next() {
        val state = _uiState.value
        val error = when (state.step) {
            CreateStep.ORIGIN -> if (state.origin == null) AppError.InvalidInput(com.example.routealarm.core.common.InvalidInputReason.MISSING_ORIGIN) else null
            CreateStep.DESTINATION -> when {
                state.destination == null -> AppError.InvalidInput(com.example.routealarm.core.common.InvalidInputReason.MISSING_DESTINATION)
                !state.useCurrentLocation && state.origin?.isSameLocationAs(state.destination) == true ->
                    AppError.InvalidInput(com.example.routealarm.core.common.InvalidInputReason.SAME_ORIGIN_AND_DESTINATION)
                else -> null
            }
            CreateStep.TIME -> if (state.repeatPreset == RepeatPreset.CUSTOM && state.customDays.isEmpty()) {
                AppError.InvalidInput(com.example.routealarm.core.common.InvalidInputReason.NO_REPEAT_DAY)
            } else {
                null
            }
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(validationError = error) }
            return
        }
        if (state.step == CreateStep.BUFFER) {
            _uiState.update { it.copy(step = CreateStep.RESULT, validationError = null) }
            calculate()
        } else {
            _uiState.update { it.copy(step = CreateStep.entries[it.stepIndex + 1], validationError = null) }
        }
    }

    /** @return false 면 첫 단계라 화면을 닫아야 한다. */
    fun back(): Boolean {
        val state = _uiState.value
        if (state.step == CreateStep.ORIGIN) return false
        if (state.result is ResultState.Saved) return false
        _uiState.update { it.copy(step = CreateStep.entries[it.stepIndex - 1], result = ResultState.Idle, validationError = null) }
        return true
    }

    fun calculate() {
        val state = _uiState.value
        _uiState.update { it.copy(result = ResultState.Calculating) }
        viewModelScope.launch {
            val result = previewSchedule(state.toDraft())
            _uiState.update {
                it.copy(
                    result = when (result) {
                        is AppResult.Success -> ResultState.Ready(result.data)
                        is AppResult.Failure -> ResultState.Failed(result.error)
                    },
                )
            }
        }
    }

    fun save() {
        val ready = _uiState.value.result as? ResultState.Ready ?: return
        viewModelScope.launch {
            when (val result = saveSchedule(ready.preview, editingScheduleId = editScheduleId)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(result = ResultState.Saved(result.data.scheduleId, inexactWarning = !result.data.registration.exact))
                }
                is AppResult.Failure -> _uiState.update { it.copy(result = ResultState.Failed(result.error)) }
            }
        }
    }

    fun consumeValidationError() = _uiState.update { it.copy(validationError = null) }

    private fun CreateScheduleUiState.toDraft() = ScheduleDraft(
        title = title,
        origin = origin,
        destination = destination,
        useCurrentLocation = useCurrentLocation,
        targetArrivalTime = targetTime,
        repeatDays = repeatDays,
        preparationMinutes = preparationMinutes,
        bufferMinutes = bufferMinutes,
        alarmMode = alarmMode,
    )
}
