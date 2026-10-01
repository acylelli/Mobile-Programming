package com.example.routealarm.presentation.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchResultState {
    data object Idle : SearchResultState
    data object Loading : SearchResultState
    data class Results(val places: List<Place>) : SearchResultState
    data class Error(val error: AppError) : SearchResultState
}

data class PlaceSearchUiState(
    val query: String = "",
    val results: SearchResultState = SearchResultState.Idle,
    /** 저장 다이얼로그에 띄울 장소 */
    val saving: Place? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class PlaceSearchViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceSearchUiState())
    val uiState: StateFlow<PlaceSearchUiState> = _uiState.asStateFlow()

    val savedPlaces: StateFlow<List<Place>> = placeRepository.observeSavedPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        // 타이핑마다 호출하지 않고 300ms 멈췄을 때만 검색한다(API 호출 절약).
        viewModelScope.launch {
            queryFlow.debounce(DEBOUNCE_MILLIS).distinctUntilChanged().collect { search(it) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
        if (query.isBlank()) _uiState.update { it.copy(results = SearchResultState.Idle) }
    }

    fun searchNow() = search(_uiState.value.query)

    private fun search(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(results = SearchResultState.Loading) }
            val result = placeRepository.search(query)
            _uiState.update {
                it.copy(
                    results = when (result) {
                        is AppResult.Success -> SearchResultState.Results(result.data)
                        is AppResult.Failure -> SearchResultState.Error(result.error)
                    },
                )
            }
        }
    }

    fun startSaving(place: Place) = _uiState.update { it.copy(saving = place) }
    fun cancelSaving() = _uiState.update { it.copy(saving = null) }

    fun confirmSave(name: String, type: PlaceType) {
        val place = _uiState.value.saving ?: return
        viewModelScope.launch {
            placeRepository.savePlace(place.copy(name = name.ifBlank { place.name }, type = type))
            _uiState.update { it.copy(saving = null) }
        }
    }

    fun deleteSaved(id: Long) {
        viewModelScope.launch { placeRepository.deletePlace(id) }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
    }
}
