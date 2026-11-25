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
 */
interface SubscriptionRepository {
    val entitlement: Flow<Entitlement>


    suspend fun current(): Entitlement

    /**
     * 서버에 주문을 만들고 브라우저로 열 결제창(토스페이먼츠 테스트 결제) 주소를 돌려준다.
     * 주문 번호는 [pendingOrderId] 로 기억해 둔다.
     */
    suspend fun startCheckout(product: SubscriptionProduct): AppResult<String>

    /** 결제창으로 보낸 뒤 아직 결과를 확인하지 못한 주문 */
    suspend fun pendingOrderId(): String?

    /**
     * 주문을 서버에 확인하고, 승인됐으면 그 기간만큼 PRO 를 적용한다.
     * 승인되거나 실패로 끝난 주문은 기억에서 지운다. 아직 결제 중(READY)이면 남겨 둔다.
     */
    suspend fun completePayment(orderId: String): AppResult<Entitlement>

    /** 코드의 기관·이용 기간·남은 인원을 확인만 한다(적용하지 않음). */
    suspend fun verifyGroupPass(code: String): AppResult<GroupPass>

    /** 단체 이용권을 적용해 PRO 로 전환한다. */
    suspend fun redeemGroupPass(code: String): AppResult<Entitlement>

    /** 무료 플랜으로 되돌린다. 일정 데이터는 삭제하지 않는다. */
    suspend fun clear()
}
