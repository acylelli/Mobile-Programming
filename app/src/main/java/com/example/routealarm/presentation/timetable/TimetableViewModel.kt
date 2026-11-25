package com.example.routealarm.presentation.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.model.TimetableAlarmProposal
import com.example.routealarm.domain.model.TimetableClass
import com.example.routealarm.domain.policy.TimetableParseResult
import com.example.routealarm.domain.policy.TimetablePolicy
import com.example.routealarm.domain.repository.PlaceRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.TimetableImageRecognizer
import com.example.routealarm.domain.repository.TimetableRepository
import com.example.routealarm.domain.usecase.CreateSchedulesFromTimetableUseCase
import com.example.routealarm.presentation.schedule.create.PlaceSearchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/** 수업 추가·수정 시트에서 고치는 값 */
data class ClassEditor(
    val id: String? = null,
    val day: DayOfWeek = DayOfWeek.MONDAY,
    val start: LocalTime = LocalTime.of(9, 0),
)

/** 알람 만들기 시트: 제안 목록과 출발지·도착지 */
data class AlarmSheetState(
    val origin: Place? = null,
    val destination: Place? = null,
    /** 장소를 고르는 중이면 어느 쪽인지(출발지 true / 도착지 false) */
    val pickingOrigin: Boolean? = null,
    val search: PlaceSearchState = PlaceSearchState.Idle,
    val isCreating: Boolean = false,
)

data class TimetableUiState(
    val classes: List<TimetableClass> = emptyList(),
    val proposals: List<TimetableAlarmProposal> = emptyList(),
    val savedPlaces: List<Place> = emptyList(),
    val editor: ClassEditor? = null,
    val alarmSheet: AlarmSheetState? = null,
    val isRecognizing: Boolean = false,
    /** 다시 만들면 새 알람으로 바뀔, 예전에 시간표로 만든 알람 수(지금 남아 있는 것만) */
    val previousAlarmCount: Int = 0,
    /** 예전 시간표 알람의 출발지·도착지. 다시 만들 때 미리 채운다. */
    val previousOrigin: Place? = null,
    val previousDestination: Place? = null,
)

sealed interface TimetableEvent {
    data class Recognized(val count: Int) : TimetableEvent
    data class ShowError(val error: AppError) : TimetableEvent

    /** 알람을 만들었다. 만들지 못한 제안이 있으면 그 개수도 알린다. */
    data class AlarmsCreated(val created: Int, val failed: Int, val replaced: Int) : TimetableEvent
}

/**
 * 학교 시간표(직접 입력·사진 인식·캘린더에서 가져온 수업)를 관리하고, 요일별 첫 수업으로 알람을 만든다.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val recognizer: TimetableImageRecognizer,
    private val placeRepository: PlaceRepository,
    private val preferencesRepository: PreferencesRepository,
    scheduleRepository: ScheduleRepository,
    private val createSchedules: CreateSchedulesFromTimetableUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(TimetableUiState())

    val uiState: StateFlow<TimetableUiState> = combine(
        timetableRepository.classes,
        placeRepository.observeSavedPlaces(),
        timetableRepository.alarmScheduleIds,
        scheduleRepository.observeSchedules(),
        local,
    ) { classes, places, alarmIds, schedules, state ->
        val previous = schedules.filter { it.id in alarmIds }
        state.copy(
            classes = classes,
            proposals = TimetablePolicy.proposals(classes),
            savedPlaces = places,
            previousAlarmCount = previous.size,
            previousOrigin = previous.firstOrNull()?.origin,
            previousDestination = previous.firstOrNull()?.destination,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimetableUiState())

    private val _events = Channel<TimetableEvent>(Channel.BUFFERED)
    val events: Flow<TimetableEvent> = _events.receiveAsFlow()

    /*
     * 글자 입력값(수업 이름·강의실·장소 검색어)은 Compose 상태로 둔다(CreateScheduleViewModel 과 같은 이유: 빠른 입력 누락 방지).
     */
    var className by mutableStateOf("")
        private set
    var classRoom by mutableStateOf("")
        private set
    var placeQuery by mutableStateOf("")
        private set

    private val placeQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            placeQueryFlow.debounce(SEARCH_DEBOUNCE_MILLIS).distinctUntilChanged().collect { searchPlaces(it) }
        }
    }

    /* ───────────── 수업 추가·수정·삭제 ───────────── */

    fun startAdding() {
        className = ""
        classRoom = ""
        local.update { it.copy(editor = ClassEditor()) }
    }

    fun startEditing(item: TimetableClass) {
        className = item.name
        classRoom = item.room.orEmpty()
        local.update { it.copy(editor = ClassEditor(item.id, item.day, item.start)) }
    }

    fun onClassNameChange(value: String) { className = value.take(MAX_NAME_LENGTH) }
    fun onClassRoomChange(value: String) { classRoom = value.take(MAX_ROOM_LENGTH) }
    fun setEditorDay(day: DayOfWeek) = local.update { it.copy(editor = it.editor?.copy(day = day)) }
    fun setEditorStart(time: LocalTime) = local.update { it.copy(editor = it.editor?.copy(start = time)) }
    fun dismissEditor() = local.update { it.copy(editor = null) }

    fun saveEditor() {
        val editor = local.value.editor ?: return
        val name = className.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            timetableRepository.upsert(
                TimetableClass(
                    id = editor.id ?: UUID.randomUUID().toString(),
                    name = name,
                    day = editor.day,
                    start = editor.start,
                    room = classRoom.trim().ifEmpty { null },
                ),
            )
            local.update { it.copy(editor = null) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { timetableRepository.remove(id) }
    }

    fun clearAll() {
        viewModelScope.launch { timetableRepository.replaceAll(emptyList()) }
    }

    /* ───────────── 사진으로 가져오기 ───────────── */

    /** 사진에서 읽은 수업으로 시간표를 바꾼다. 인식이 완벽하지 않으므로 사용자가 목록에서 확인·수정한다. */
    fun importImage(imageUri: String) {
        if (local.value.isRecognizing) return
        local.update { it.copy(isRecognizing = true) }
        viewModelScope.launch {
            when (val result = recognizer.recognize(imageUri)) {
                is AppResult.Success -> when (val parsed = result.data) {
                    is TimetableParseResult.Success -> if (parsed.classes.isEmpty()) {
                        _events.send(TimetableEvent.ShowError(AppError.TimetableNotRecognized))
                    } else {
                        timetableRepository.replaceAll(
                            parsed.classes.map { TimetableClass(UUID.randomUUID().toString(), it.name, it.day, it.start, room = it.room) },
                        )
                        _events.send(TimetableEvent.Recognized(parsed.classes.size))
                    }
                    TimetableParseResult.NoDayHeader, TimetableParseResult.NoHourLabels ->
                        _events.send(TimetableEvent.ShowError(AppError.TimetableNotRecognized))
                }
                is AppResult.Failure -> _events.send(TimetableEvent.ShowError(AppError.TimetableNotRecognized))
            }
            local.update { it.copy(isRecognizing = false) }
        }
    }

    /* ───────────── 알람 만들기 ───────────── */

    fun openAlarmSheet() {
        // 예전 시간표 알람의 장소, 없으면 즐겨찾기의 집·학교를 미리 골라 둔다.
        val state = uiState.value
        val places = state.savedPlaces
        local.update {
            it.copy(
                alarmSheet = AlarmSheetState(
                    origin = state.previousOrigin ?: places.firstOrNull { p -> p.type == PlaceType.HOME },
                    destination = state.previousDestination ?: places.firstOrNull { p -> p.type == PlaceType.SCHOOL },
                ),
            )
        }
    }

    fun dismissAlarmSheet() = local.update { it.copy(alarmSheet = null) }

    fun startPicking(origin: Boolean) {
        placeQuery = ""
        placeQueryFlow.value = ""
        local.update { it.copy(alarmSheet = it.alarmSheet?.copy(pickingOrigin = origin, search = PlaceSearchState.Idle)) }
    }

    fun cancelPicking() = local.update { it.copy(alarmSheet = it.alarmSheet?.copy(pickingOrigin = null)) }

    fun onPlaceQueryChange(value: String) {
        placeQuery = value
        placeQueryFlow.value = value
    }

    fun searchPlacesNow() = searchPlaces(placeQuery)

    private fun searchPlaces(keyword: String) {
        searchJob?.cancel()
        if (keyword.isBlank()) {
            local.update { it.copy(alarmSheet = it.alarmSheet?.copy(search = PlaceSearchState.Idle)) }
            return
        }
        local.update { it.copy(alarmSheet = it.alarmSheet?.copy(search = PlaceSearchState.Loading)) }
        searchJob = viewModelScope.launch {
            val search = when (val result = placeRepository.search(keyword.trim())) {
                is AppResult.Success -> PlaceSearchState.Results(result.data)
                is AppResult.Failure -> PlaceSearchState.Error(result.error)
            }
            local.update { it.copy(alarmSheet = it.alarmSheet?.copy(search = search)) }
        }
    }

    fun pickPlace(place: Place) {
        local.update { state ->
            val sheet = state.alarmSheet ?: return@update state
            state.copy(
                alarmSheet = when (sheet.pickingOrigin) {
                    true -> sheet.copy(origin = place, pickingOrigin = null)
                    false -> sheet.copy(destination = place, pickingOrigin = null)
                    null -> sheet
                },
            )
        }
    }

    fun createAlarms() {
        val sheet = local.value.alarmSheet ?: return
        val origin = sheet.origin ?: return
        val destination = sheet.destination ?: return
        if (sheet.isCreating) return
        local.update { it.copy(alarmSheet = sheet.copy(isCreating = true)) }
        viewModelScope.launch {
            val prefs = preferencesRepository.current()
            val result = createSchedules(
                proposals = uiState.value.proposals,
                origin = origin,
                destination = destination,
                preparationMinutes = prefs.defaultPreparationMinutes,
                bufferMinutes = prefs.defaultBufferMinutes,
            )
            local.update { it.copy(alarmSheet = null) }
            when {
                result.created.isNotEmpty() -> _events.send(TimetableEvent.AlarmsCreated(result.created.size, result.failed.size, result.replaced))
                result.failed.isNotEmpty() -> _events.send(TimetableEvent.ShowError(result.failed.first().second))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val MAX_NAME_LENGTH = 40
        const val MAX_ROOM_LENGTH = 20
    }
}
