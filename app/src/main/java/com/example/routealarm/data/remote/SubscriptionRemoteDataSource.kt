package com.example.routealarm.data.remote

import com.example.routealarm.data.remote.dto.GroupPassDto
import com.example.routealarm.data.remote.dto.SubscriptionStatusDto

/**
 * 결제·이용권 검증 교체 지점.
 *
 * 실제 서비스에서는 이 인터페이스의 구현이 Google Play Billing 으로 구매를 진행한 뒤
 * 자체 서버(→ Google Play 개발자 API)의 영수증 검증 결과를 돌려준다.
 * 현재는 [com.example.routealarm.data.remote.fake.FakeSubscriptionRemoteDataSource] 만 연결되어 있어
 * Play Console 상품이나 서버 없이 구독 흐름 전체를 시연할 수 있다.
 */
interface SubscriptionRemoteDataSource {
    /** 실제 결제가 일어나지 않는 시연용 구현이면 true */
    val isSimulated: Boolean

    /** 정기 결제를 시작하고 서버가 검증한 구독 상태를 돌려준다. */
    suspend fun startSubscription(productId: String): SubscriptionStatusDto

    /** @throws GroupPassNotFoundException 등록되지 않은 코드 */
    suspend fun verifyGroupPass(code: String): GroupPassDto

    /** 이용권을 사용 처리한다(남은 인원 1 감소). */
    suspend fun redeemGroupPass(code: String): GroupPassDto
}

class GroupPassNotFoundException(code: String) : Exception("Unknown group pass: $code")
