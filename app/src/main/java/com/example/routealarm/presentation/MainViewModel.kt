package com.example.routealarm.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.ColorTheme
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.usecase.CompletePaymentUseCase
import com.example.routealarm.domain.usecase.ResumePendingPaymentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class MainUiState(
    val isLoading: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
)

/** 결제창에서 돌아왔을 때 보여 줄 결과 */
enum class PaymentMessage { CHECKING, SUCCESS, NOT_COMPLETED }

/** 앱 진입 상태(온보딩 여부, 테마). Splash 는 이 값이 로드될 때까지만 유지된다. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val completePayment: CompletePaymentUseCase,
    private val resumePendingPayment: ResumePendingPaymentUseCase,
) : ViewModel() {

    /** 딥링크로 돌아온 확인과 앱 복귀 시 확인이 겹쳐도 한 번만 적용·안내되도록 한다. */
    private val paymentLock = Mutex()

    private val _paymentMessages = Channel<PaymentMessage>(Channel.BUFFERED)
    val paymentMessages: Flow<PaymentMessage> = _paymentMessages.receiveAsFlow()

    /**
     * 결제창에서 routealarm://payment 로 돌아왔다. 링크의 result 값은 믿지 않고(다른 앱도 이 링크를 열 수 있다)
     * 서버에 주문 상태를 물어 승인된 경우에만 PRO 를 적용한다.
     */
    fun onPaymentReturn(orderId: String) {
        viewModelScope.launch {
            paymentLock.withLock {
                _paymentMessages.send(PaymentMessage.CHECKING)
                val message = when (completePayment(orderId)) {
                    is AppResult.Success -> PaymentMessage.SUCCESS
                    is AppResult.Failure -> PaymentMessage.NOT_COMPLETED
                }
                _paymentMessages.send(message)
            }
        }
    }

    /**
     * 앱이 다시 보일 때마다 결과를 확인하지 못한 주문이 있는지 본다.
     * 승인된 경우에만 안내하고, 아직 결제 중이거나 확인할 주문이 없으면 조용히 넘어간다.
     */
    fun onAppResumed() {
        viewModelScope.launch {
            paymentLock.withLock {
                if (resumePendingPayment() is AppResult.Success) _paymentMessages.send(PaymentMessage.SUCCESS)
            }
        }
    }

    val uiState: StateFlow<MainUiState> = preferencesRepository.preferences
        .map { MainUiState(isLoading = false, onboardingCompleted = it.onboardingCompleted, colorTheme = it.colorTheme) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    fun completeOnboarding() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted() }
    }
}
