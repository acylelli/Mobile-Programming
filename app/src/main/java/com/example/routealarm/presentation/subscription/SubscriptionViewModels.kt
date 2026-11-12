package com.example.routealarm.presentation.subscription

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.GroupPass
import com.example.routealarm.domain.model.SubscriptionProduct
import com.example.routealarm.domain.repository.SubscriptionRepository
import com.example.routealarm.domain.usecase.ApplyGroupPassUseCase
import com.example.routealarm.domain.usecase.GroupPassCode
import com.example.routealarm.domain.usecase.ResetSubscriptionUseCase
import com.example.routealarm.domain.usecase.StartProTrialUseCase
import com.example.routealarm.domain.usecase.VerifyGroupPassUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/* ───────────────────────── PRO 페이월 (UI-E-001) ───────────────────────── */

/** 페이월에서 고를 수 있는 요금제. 무료로 계속하는 것도 하나의 선택지다. */
enum class PaywallOption(val product: SubscriptionProduct?) {
    YEARLY(SubscriptionProduct.YEARLY),
    MONTHLY(SubscriptionProduct.MONTHLY),
    FREE(null),
}

data class PaywallUiState(
    val option: PaywallOption = PaywallOption.YEARLY,
    val isProcessing: Boolean = false,
    val error: AppError? = null,
    /** 실제 결제가 일어나지 않는 시연용 빌드인지(화면에 안내 표시) */
    val isSimulated: Boolean = false,
)

sealed interface PaywallEvent {
    data object Subscribed : PaywallEvent
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    subscriptionRepository: SubscriptionRepository,
    private val startProTrial: StartProTrialUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState(isSimulated = subscriptionRepository.isSimulated))
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    private val _events = Channel<PaywallEvent>(Channel.BUFFERED)
    val events: Flow<PaywallEvent> = _events.receiveAsFlow()

    /** 시트를 다시 열 때마다 추천 요금제(연간)에서 시작한다. */
    fun reset() = _uiState.update { it.copy(option = PaywallOption.YEARLY, isProcessing = false, error = null) }

    fun select(option: PaywallOption) = _uiState.update { it.copy(option = option, error = null) }

    /** 7일 무료체험을 시작하고 결제 창을 호출한다(④). */
    fun startTrial() {
        val state = _uiState.value
        val product = state.option.product ?: return
        if (state.isProcessing) return
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            when (val result = startProTrial(product)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.send(PaywallEvent.Subscribed)
                }
                is AppResult.Failure -> _uiState.update { it.copy(isProcessing = false, error = result.error) }
            }
        }
    }
}

/* ───────────────────────── 단체 이용권 코드 입력 (UI-E-002) ───────────────────────── */

sealed interface GroupPassCheck {
    data object Idle : GroupPassCheck
    data object Checking : GroupPassCheck
    data class Verified(val pass: GroupPass) : GroupPassCheck
    data class Failed(val error: AppError) : GroupPassCheck
}

data class GroupPassUiState(
    val check: GroupPassCheck = GroupPassCheck.Idle,
    val isApplying: Boolean = false,
    val isSimulated: Boolean = false,
)

sealed interface GroupPassEvent {
    /** @property hadPersonalSubscription true 면 개인 구독 자동 갱신 해지를 안내해야 한다. */
    data class Applied(val organization: String, val hadPersonalSubscription: Boolean) : GroupPassEvent
    data class ShowError(val error: AppError) : GroupPassEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class GroupPassViewModel @Inject constructor(
    subscriptionRepository: SubscriptionRepository,
    private val verifyGroupPass: VerifyGroupPassUseCase,
    private val applyGroupPass: ApplyGroupPassUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupPassUiState(isSimulated = subscriptionRepository.isSimulated))
    val uiState: StateFlow<GroupPassUiState> = _uiState.asStateFlow()

    private val _events = Channel<GroupPassEvent>(Channel.BUFFERED)
    val events: Flow<GroupPassEvent> = _events.receiveAsFlow()

    /**
     * 입력 중인 코드(사용자가 친 그대로). 입력 즉시 반영되도록 StateFlow 가 아닌 Compose 상태로 둔다.
     * 입력 도중에 글자를 대문자로 바꿔 끼우면 키보드가 알고 있는 내용과 어긋나 글자가 빠질 수 있으므로,
     * 대문자 변환은 화면 표시(입력창)와 서버 확인 단계에서만 한다.
     */
    var code by mutableStateOf("")
        private set

    private val codeFlow = MutableStateFlow("")
    private var verifyJob: Job? = null

    init {
        // 입력이 멈추고 코드가 완성된 형태일 때만 서버에 확인한다.
        viewModelScope.launch {
            codeFlow.debounce(VERIFY_DEBOUNCE_MILLIS).distinctUntilChanged().collect { code ->
                if (GroupPassCode.isComplete(code)) verify(code)
            }
        }
    }

    /** 코드 입력(①): 영문은 대문자로 자동 변환해 보여주고 확인한다. */
    fun onCodeChange(raw: String) {
        val input = GroupPassCode.sanitize(raw)
        if (input == code) return
        val changed = !input.equals(code, ignoreCase = true)
        code = input
        if (!changed) return
        verifyJob?.cancel()
        _uiState.update { it.copy(check = GroupPassCheck.Idle) }
        codeFlow.value = GroupPassCode.normalize(input)
    }

    private fun verify(target: String) {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            _uiState.update { it.copy(check = GroupPassCheck.Checking) }
            val result = verifyGroupPass(target)
            _uiState.update {
                // 확인하는 사이 코드를 고쳤다면 이전 결과는 버린다.
                if (GroupPassCode.normalize(code) != target) {
                    it
                } else {
                    it.copy(
                        check = when (result) {
                            is AppResult.Success -> GroupPassCheck.Verified(result.data)
                            is AppResult.Failure -> GroupPassCheck.Failed(result.error)
                        },
                    )
                }
            }
        }
    }

    /** 적용(⑤): PRO 를 적용한다. */
    fun apply() {
        val state = _uiState.value
        if (state.isApplying || state.check !is GroupPassCheck.Verified) return
        _uiState.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            when (val result = applyGroupPass(code)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isApplying = false) }
                    _events.send(
                        GroupPassEvent.Applied(
                            organization = result.data.entitlement.organization.orEmpty(),
                            hadPersonalSubscription = result.data.hadPersonalSubscription,
                        ),
                    )
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isApplying = false) }
                    _events.send(GroupPassEvent.ShowError(result.error))
                }
            }
        }
    }

    private companion object {
        const val VERIFY_DEBOUNCE_MILLIS = 400L
    }
}

/* ───────────────────────── 구독 관리 (UI-F-003) ───────────────────────── */

data class SubscriptionUiState(
    val entitlement: Entitlement = Entitlement.Free,
    val isPro: Boolean = false,
    val isInTrial: Boolean = false,
    val isSimulated: Boolean = false,
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    subscriptionRepository: SubscriptionRepository,
    private val resetSubscription: ResetSubscriptionUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<SubscriptionUiState> = subscriptionRepository.entitlement
        .map { entitlement ->
            val now = timeProvider.now()
            SubscriptionUiState(
                entitlement = entitlement,
                isPro = entitlement.isPro(now),
                isInTrial = entitlement.isInTrial(now),
                isSimulated = subscriptionRepository.isSimulated,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SubscriptionUiState(isSimulated = subscriptionRepository.isSimulated),
        )

    /** 시연용: 결제가 실제로 일어나지 않았으므로 앱 안에서 무료 플랜으로 되돌릴 수 있게 한다. */
    fun resetToFree() {
        viewModelScope.launch { resetSubscription() }
    }
}
