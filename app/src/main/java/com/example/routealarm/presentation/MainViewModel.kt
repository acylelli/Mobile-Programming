package com.example.routealarm.presentation

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

data class MainUiState(
    val isLoading: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
)

/** 앱 진입 상태(온보딩 여부, 테마). Splash 는 이 값이 로드될 때까지만 유지된다. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> = preferencesRepository.preferences
        .map { MainUiState(isLoading = false, onboardingCompleted = it.onboardingCompleted, colorTheme = it.colorTheme) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    fun completeOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted() }
    }
}
