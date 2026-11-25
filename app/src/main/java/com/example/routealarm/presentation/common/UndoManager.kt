package com.example.routealarm.presentation.common

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.example.routealarm.core.common.ApplicationScope
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.usecase.RestoreScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 되돌릴 수 있는 알람 변경 하나: 보여 줄 문구와 바꾸기 직전의 일정 */
data class UndoOffer(@param:StringRes val messageRes: Int, val snapshot: Schedule)

/**
 * 알람 변경 되돌리기. 화면(ViewModel)은 바꾸기 직전 일정을 [offer] 하고,
 * 앱 최상위가 "되돌리기" 스낵바를 띄운다. 화면을 떠나도(예: 상세에서 삭제 후 목록으로) 스낵바와 되돌리기가 유지되도록
 * 화면 밖(앱 범위)에서 처리한다.
 */
@Singleton
class UndoManager @Inject constructor(
    private val restoreSchedule: RestoreScheduleUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _offers = MutableSharedFlow<UndoOffer>(extraBufferCapacity = 4)
    val offers: SharedFlow<UndoOffer> = _offers.asSharedFlow()

    fun offer(@StringRes messageRes: Int, snapshot: Schedule) {
        _offers.tryEmit(UndoOffer(messageRes, snapshot))
    }

    fun undo(offer: UndoOffer) {
        scope.launch { restoreSchedule(offer.snapshot) }
    }
}

/** 앱 최상위 Compose 에서 [UndoManager] 를 쓰기 위한 얇은 ViewModel */
@HiltViewModel
class UndoViewModel @Inject constructor(private val undoManager: UndoManager) : ViewModel() {
    val offers: SharedFlow<UndoOffer> = undoManager.offers
    fun undo(offer: UndoOffer) = undoManager.undo(offer)
}
