package com.example.routealarm.presentation.schedule.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.InvalidInputReason
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.repository.LocationRepository
import com.example.routealarm.domain.repository.PlaceRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.SubscriptionRepository
import com.example.routealarm.domain.usecase.PreviewDelayScenarioUseCase
import com.example.routealarm.domain.usecase.PreviewScheduleUseCase
import com.example.routealarm.domain.usecase.SaveScheduleUseCase
import com.example.routealarm.domain.usecase.ScheduleDraft
import com.example.routealarm.domain.usecase.SchedulePreview
import com.example.routealarm.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class CreateStep { ORIGIN, DESTINATION, TIME, PREPARATION, BUFFER, RESULT }

enum class BufferPreset(val minutes: Int) { TIGHT(5), NORMAL(10), RELAXED(20) }

enum class RepeatPreset { ONCE, WEEKDAYS, DAILY, CUSTOM }

sealed interface ResultState {
    data object Idle : ResultState
    data object Calculating : ResultState

    /** @property delayScenarioWakeUp 15분 지연이 생겼을 때 다시 계산한 기상 시각(지연 시나리오 카드) */
    data class Ready(val preview: SchedulePreview, val delayScenarioWakeUp: Instant? = null) : ResultState
    data class Saved(val scheduleId: Long, val inexactWarning: Boolean) : ResultState
    data class Failed(val error: AppError) : ResultState
}

/** 장소·주소 검색(UI-C-002 ②)의 진행 상태 */
sealed interface PlaceSearchState {
    data object Idle : PlaceSearchState
    data object Loading : PlaceSearchState
    data class Results(val places: List<Place>) : PlaceSearchState
    data class Error(val error: AppError) : PlaceSearchState
}

data class CreateScheduleUiState(
    val step: CreateStep = CreateStep.ORIGIN,
    val isEditing: Boolean = false,
    val origin: Place? = null,
    val destination: Place? = null,
    val useCurrentLocation: Boolean = false,
    val targetTime: LocalTime = LocalTime.of(9, 0),
    /** 반복 요일. 비어 있으면 한 번만 울리는 일정 */
    val repeatDays: Set<DayOfWeek> = WEEKDAYS,
    /** 1회 일정의 날짜. null 이면 가장 가까운 날(오늘 또는 내일) */
    val oneTimeDate: LocalDate? = null,
    val preparationMinutes: Int = 30,
    val bufferPreset: BufferPreset? = BufferPreset.NORMAL,
    val customBufferMinutes: Int = 10,
    val alarmMode: AlarmMode = AlarmMode.WAKE_UP_AND_DEPARTURE,
    val search: PlaceSearchState = PlaceSearchState.Idle,
    /** 즐겨찾기 빈 칸(회사 등)에 넣을 장소를 검색하는 중이면 그 종류 */
    val registeringType: PlaceType? = null,
    val result: ResultState = ResultState.Idle,
    /** 미리보기 화면에서 반복 요일을 바꿔 다시 계산하는 중(기존 결과는 그대로 보여준다) */
    val isRecalculating: Boolean = false,
    val isSaving: Boolean = false,
    val validationError: AppError? = null,
) {
    val repeatPreset: RepeatPreset
        get() = when {
            repeatDays.isEmpty() -> RepeatPreset.ONCE
            repeatDays == WEEKDAYS -> RepeatPreset.WEEKDAYS
            repeatDays.size == DayOfWeek.entries.size -> RepeatPreset.DAILY
            else -> RepeatPreset.CUSTOM
        }
    val bufferMinutes: Int get() = bufferPreset?.minutes ?: customBufferMinutes
    val stepIndex: Int get() = CreateStep.entries.indexOf(step)
    val stepCount: Int get() = CreateStep.entries.size - 1 // RESULT(계산 미리보기)는 단계 수에서 제외

    /** 현재 단계에서 고른 장소(출발지 또는 도착지) */
    val selectedPlace: Place?
        get() = when (step) {
            CreateStep.ORIGIN -> origin
            CreateStep.DESTINATION -> destination
            else -> null
        }

    /** [다음] 버튼 활성화 여부. 장소 단계는 장소를 골라야 넘어갈 수 있다(UI-C-002 ⑤). */
    val canProceed: Boolean
        get() = when (step) {
            CreateStep.ORIGIN -> origin != null
            CreateStep.DESTINATION -> destination != null
            else -> true
        }

    companion object {
        val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    }
}

/**
 * 새 일정 5단계 + 계산 미리보기(UI-C-001 ~ 006). 모든 입력은 ViewModel 에 호이스팅되고 화면은 Stateless 다.
 * 단계 사이를 오가도 입력값이 유지되며, 계산(Preview)과 저장(Save)을 분리해 사용자가 결과를 보고 결정할 수 있게 한다.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CreateScheduleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val previewSchedule: PreviewScheduleUseCase,
    private val previewDelayScenario: PreviewDelayScenarioUseCase,
    private val saveSchedule: SaveScheduleUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    subscriptionRepository: SubscriptionRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val editScheduleId: Long? = savedStateHandle.toRoute<Route.CreateSchedule>().editScheduleId

    private val _uiState = MutableStateFlow(CreateScheduleUiState(isEditing = editScheduleId != null))
    val uiState: StateFlow<CreateScheduleUiState> = _uiState.asStateFlow()

    /*
     * 글자 입력값(검색어, 일정 이름)은 StateFlow 가 아니라 Compose 상태로 둔다.
     * 설계 이유: StateFlow 로 보낸 값은 한 프레임 뒤에야 입력창으로 돌아온다. 그 사이에 들어온 키 입력은
     * 이전 값을 기준으로 처리되어, 빠르게 입력하면 글자가 빠진다. Compose 상태는 같은 호출 안에서 바로 반영된다.
     */
    var query by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set

    val savedPlaces: StateFlow<List<Place>> = placeRepository.observeSavedPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val recentSearches: StateFlow<List<Place>> = placeRepository.observeRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val isPro: StateFlow<Boolean> = subscriptionRepository.entitlement
        .map { it.isPro(timeProvider.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null
    private var calculateJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.current()
            val existing = editScheduleId?.let { scheduleRepository.getSchedule(it) }
            _uiState.update { state ->
                if (existing != null) {
                    title = existing.title
                    state.copy(
                        origin = existing.origin,
                        destination = existing.destination,
                        useCurrentLocation = existing.useCurrentLocation,
                        targetTime = existing.targetArrivalTime,
                        repeatDays = existing.repeatDays,
                        // 지난 날짜는 다시 쓰지 않는다. null 로 두면 가장 가까운 날로 다시 잡힌다.
                        oneTimeDate = existing.oneTimeDate?.takeIf { !it.isBefore(timeProvider.nowZoned().toLocalDate()) },
                        preparationMinutes = existing.preparationMinutes,
                        bufferPreset = BufferPreset.entries.firstOrNull { it.minutes == existing.bufferMinutes },
                        customBufferMinutes = existing.bufferMinutes,
                        alarmMode = existing.alarmMode,
                    )
                } else {
                    state.copy(
                        preparationMinutes = prefs.defaultPreparationMinutes,
                        bufferPreset = BufferPreset.entries.firstOrNull { it.minutes == prefs.defaultBufferMinutes },
                        customBufferMinutes = prefs.defaultBufferMinutes,
                    )
                }
            }
        }
        // 타이핑마다 호출하지 않고 300ms 멈췄을 때만 검색한다(API 호출 절약).
        viewModelScope.launch {
            queryFlow.debounce(SEARCH_DEBOUNCE_MILLIS).distinctUntilChanged().collect { search(it) }
        }
    }

    /* ───────────── 1·2단계: 출발지 / 도착지 ───────────── */

    fun onQueryChange(value: String) {
        query = value
        if (value.isBlank()) _uiState.update { it.copy(search = PlaceSearchState.Idle) }
        queryFlow.value = value
    }

    fun searchNow() = search(query)

    private fun clearQuery() {
        searchJob?.cancel()
        query = ""
        queryFlow.value = ""
    }

    private fun search(keyword: String) {
        searchJob?.cancel()
        if (keyword.isBlank()) return
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(search = PlaceSearchState.Loading) }
            val result = placeRepository.search(keyword)
            _uiState.update {
                // 그 사이 검색어를 지웠다면 결과를 버린다.
                if (query.isBlank()) {
                    it
                } else {
                    it.copy(
                        search = when (result) {
                            is AppResult.Success -> PlaceSearchState.Results(result.data)
                            is AppResult.Failure -> PlaceSearchState.Error(result.error)
                        },
                    )
                }
            }
        }
    }

    /**
     * 현재 단계의 장소를 고른다. 검색 결과에서 골랐다면 최근 검색에 남기고,
     * 즐겨찾기 빈 칸을 채우는 중이었다면 그 종류(집/학교/회사)로 저장한다.
     */
    fun selectPlace(place: Place, fromSearch: Boolean = false) {
        val registering = _uiState.value.registeringType
        viewModelScope.launch {
            var chosen = place
            if (registering != null) {
                val favorite = place.copy(id = 0, type = registering, isDemo = false)
                val id = (placeRepository.savePlace(favorite) as? AppResult.Success)?.data ?: 0
                chosen = favorite.copy(id = id)
            } else if (fromSearch) {
                placeRepository.addRecentSearch(place)
            }
            clearQuery()
            _uiState.update { state ->
                val cleared = state.cleared()
                if (state.step == CreateStep.ORIGIN) {
                    cleared.copy(origin = chosen, useCurrentLocation = false)
                } else {
                    cleared.copy(destination = chosen)
                }
            }
        }
    }

    fun hasLocationPermission(): Boolean = locationRepository.hasLocationPermission()

    /** 현재 위치를 출발지로 쓴다. 좌표는 지금 저장하지 않고 계산하는 순간에 1회만 조회한다(SFR-002). */
    fun useCurrentLocation(placeholderName: String) {
        clearQuery()
        _uiState.update {
            it.cleared().copy(
                useCurrentLocation = true,
                origin = Place(name = placeholderName, address = "", latitude = 0.0, longitude = 0.0),
            )
        }
    }

    fun startRegistering(type: PlaceType) = _uiState.update { it.copy(registeringType = type) }

    fun cancelRegistering() {
        clearQuery()
        _uiState.update { it.copy(registeringType = null, search = PlaceSearchState.Idle) }
    }

    fun deleteFavorite(placeId: Long) {
        viewModelScope.launch { placeRepository.deletePlace(placeId) }
    }

    /* ───────────── 3단계: 도착 시각, 반복 ───────────── */

    fun onTitleChange(value: String) {
        title = value.take(TITLE_MAX_LENGTH)
    }
    fun setTargetTime(time: LocalTime) = _uiState.update { it.copy(targetTime = time) }
    fun setOneTimeDate(date: LocalDate?) = _uiState.update { it.copy(oneTimeDate = date) }

    fun setRepeatPreset(preset: RepeatPreset) {
        val days = when (preset) {
            RepeatPreset.ONCE -> emptySet()
            RepeatPreset.WEEKDAYS -> CreateScheduleUiState.WEEKDAYS
            RepeatPreset.DAILY -> DayOfWeek.entries.toSet()
            RepeatPreset.CUSTOM -> _uiState.value.repeatDays
        }
        updateRepeatDays(days)
    }

    /** 요일을 하나씩 눌러 반복을 직접 지정한다. 모두 끄면 "한 번" 일정이 된다. */
    fun toggleRepeatDay(day: DayOfWeek) {
        val current = _uiState.value.repeatDays
        updateRepeatDays(if (day in current) current - day else current + day)
    }

    private fun updateRepeatDays(days: Set<DayOfWeek>) {
        if (days == _uiState.value.repeatDays) return
        _uiState.update { it.copy(repeatDays = days) }
        // 미리보기 화면에서 바꿨다면 다음 회차가 달라지므로 다시 계산한다.
        if (_uiState.value.step == CreateStep.RESULT) calculate()
    }

    /* ───────────── 4·5단계: 준비 시간, 여유 시간 ───────────── */

    fun setPreparation(minutes: Int) = _uiState.update { it.copy(preparationMinutes = minutes) }
    fun setBufferPreset(preset: BufferPreset?) = _uiState.update { it.copy(bufferPreset = preset) }
    fun setCustomBuffer(minutes: Int) = _uiState.update { it.copy(customBufferMinutes = minutes, bufferPreset = null) }
    fun setAlarmMode(mode: AlarmMode) = _uiState.update { it.copy(alarmMode = mode) }

    /** 다음 단계로. 현재 단계 입력을 검증하고 마지막 단계면 계산을 시작한다. */
    fun next() {
        val state = _uiState.value
        val error = when (state.step) {
            CreateStep.ORIGIN -> if (state.origin == null) AppError.InvalidInput(InvalidInputReason.MISSING_ORIGIN) else null
            CreateStep.DESTINATION -> when {
                state.destination == null -> AppError.InvalidInput(InvalidInputReason.MISSING_DESTINATION)
                !state.useCurrentLocation && state.origin?.isSameLocationAs(state.destination) == true ->
                    AppError.InvalidInput(InvalidInputReason.SAME_ORIGIN_AND_DESTINATION)
                else -> null
            }
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(validationError = error) }
            return
        }
        clearQuery()
        if (state.step == CreateStep.BUFFER) {
            _uiState.update { it.cleared().copy(step = CreateStep.RESULT) }
            calculate()
        } else {
            _uiState.update { it.cleared().copy(step = CreateStep.entries[it.stepIndex + 1]) }
        }
    }

    /** @return false 면 첫 단계라 화면을 닫아야 한다. */
    fun back(): Boolean {
        val state = _uiState.value
        if (state.result is ResultState.Saved) return false
        // 검색 중이거나 즐겨찾기 등록 중이면 먼저 그 상태에서 빠져나온다.
        if (query.isNotEmpty() || state.registeringType != null) {
            cancelRegistering()
            return true
        }
        if (state.step == CreateStep.ORIGIN) return false
        calculateJob?.cancel()
        _uiState.update {
            it.cleared().copy(step = CreateStep.entries[it.stepIndex - 1], result = ResultState.Idle, isRecalculating = false)
        }
        return true
    }

    private fun CreateScheduleUiState.cleared() =
        copy(search = PlaceSearchState.Idle, registeringType = null, validationError = null)

    /* ───────────── 계산 미리보기 ───────────── */

    fun calculate() {
        val state = _uiState.value
        // 이미 보여준 결과가 있으면(요일 변경) 화면을 비우지 않고 그 위에서 다시 계산한다.
        val keepPrevious = state.result is ResultState.Ready
        _uiState.update {
            if (keepPrevious) it.copy(isRecalculating = true) else it.copy(result = ResultState.Calculating, isRecalculating = false)
        }
        calculateJob?.cancel()
        calculateJob = viewModelScope.launch {
            val result = previewSchedule(state.toDraft())
            _uiState.update {
                it.copy(
                    isRecalculating = false,
                    result = when (result) {
                        is AppResult.Success -> ResultState.Ready(result.data, previewDelayScenario(result.data))
                        is AppResult.Failure -> ResultState.Failed(result.error)
                    },
                )
            }
        }
    }

    /** 저장하면 정확한 알람과 교통 갱신 작업이 등록된다(UI-C-006 ⑥). */
    fun save() {
        val state = _uiState.value
        val ready = state.result as? ResultState.Ready ?: return
        if (state.isSaving || state.isRecalculating) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = saveSchedule(ready.preview, editingScheduleId = editScheduleId)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    result = when (result) {
                        is AppResult.Success -> ResultState.Saved(result.data.scheduleId, inexactWarning = !result.data.registration.exact)
                        is AppResult.Failure -> ResultState.Failed(result.error)
                    },
                )
            }
        }
    }

    fun consumeValidationError() = _uiState.update { it.copy(validationError = null) }

    private fun CreateScheduleUiState.toDraft() = ScheduleDraft(
        title = title.trim(),
        origin = origin,
        destination = destination,
        useCurrentLocation = useCurrentLocation,
        targetArrivalTime = targetTime,
        repeatDays = repeatDays,
        oneTimeDate = oneTimeDate.takeIf { repeatDays.isEmpty() },
        preparationMinutes = preparationMinutes,
        bufferMinutes = bufferMinutes,
        alarmMode = alarmMode,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val TITLE_MAX_LENGTH = 30
    }
}
