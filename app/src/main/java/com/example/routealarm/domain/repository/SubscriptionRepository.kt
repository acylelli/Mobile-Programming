package com.example.routealarm.domain.repository

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.GroupPass
import com.example.routealarm.domain.model.SubscriptionProduct
import kotlinx.coroutines.flow.Flow

/**
 * 구독·단체 이용권 상태의 유일한 진입점.
 *
 * 설계 이유: 결제(Google Play Billing)와 검증 서버를 Domain 에서 숨긴다. 구현체는 서버가 검증해 준 결과만
 * 저장하고, 네트워크 오류 시에는 마지막으로 확인된 상태를 그대로 돌려준다(SFR-015).
 * 교통 데이터와 같은 방식으로, 결제 연동이 준비되기 전에는 시연용 구현이 이 자리에 들어간다.
 */
interface SubscriptionRepository {
    val entitlement: Flow<Entitlement>

    /** true 면 실제 결제 없이 시연용으로 동작한다. 화면은 이 값을 보고 안내 문구를 표시한다. */
    val isSimulated: Boolean

    suspend fun current(): Entitlement

    /** 7일 무료체험으로 정기 결제를 시작한다. */
    suspend fun startTrial(product: SubscriptionProduct): AppResult<Entitlement>

    /** 코드의 기관·이용 기간·남은 인원을 확인만 한다(적용하지 않음). */
    suspend fun verifyGroupPass(code: String): AppResult<GroupPass>

    /** 단체 이용권을 적용해 PRO 로 전환한다. */
    suspend fun redeemGroupPass(code: String): AppResult<Entitlement>

    /** 무료 플랜으로 되돌린다. 일정 데이터는 삭제하지 않는다. */
    suspend fun clear()
}
