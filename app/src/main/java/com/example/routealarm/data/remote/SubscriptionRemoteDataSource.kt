package com.example.routealarm.data.remote

import com.example.routealarm.data.remote.dto.GroupPassDto

/**
 * 단체 이용권 확인·사용. Proxy 서버(GET/POST /v1/group-passes)가 기관·기간·남은 인원을 관리한다.
 * 개인 PRO 결제는 [PaymentRemoteDataSource] 가 담당한다.
 */
interface SubscriptionRemoteDataSource {
    /** @throws GroupPassNotFoundException 등록되지 않은 코드 */
    suspend fun verifyGroupPass(code: String): GroupPassDto

    /** 이용권을 사용 처리한다(남은 인원 1 감소). */
    suspend fun redeemGroupPass(code: String): GroupPassDto
}

class GroupPassNotFoundException(code: String) : Exception("Unknown group pass: $code")
