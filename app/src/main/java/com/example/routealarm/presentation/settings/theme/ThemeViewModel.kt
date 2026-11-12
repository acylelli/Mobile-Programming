package com.example.routealarm.presentation.settings.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.domain.model.ColorTheme
import com.example.routealarm.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThemeUiState(
    val savedTheme: ColorTheme = ColorTheme.DEFAULT,
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<ThemeUiState> = preferencesRepository.preferences
        .map { ThemeUiState(savedTheme = it.colorTheme) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeUiState())

    /** 선택한 컬러 테마를 저장한다. 저장하는 즉시 앱 전체의 색 토큰에 반영된다. */
    fun apply(theme: ColorTheme) {
        viewModelScope.launch { preferencesRepository.setColorTheme(theme) }
    }
}
