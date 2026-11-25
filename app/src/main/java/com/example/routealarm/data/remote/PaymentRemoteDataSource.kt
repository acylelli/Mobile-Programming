package com.example.routealarm.data.remote

import com.example.routealarm.data.remote.dto.CreateOrderRequestDto
import com.example.routealarm.data.remote.dto.CreateOrderResponseDto
import com.example.routealarm.data.remote.dto.PaymentResultDto
import com.example.routealarm.data.remote.proxy.RouteAlarmProxyApi
import javax.inject.Inject

/**
 * PRO 기간제 이용권 결제 교체 지점.
 *
 * [TossTestPaymentRemoteDataSource]: Proxy 서버가 띄우는 토스페이먼츠 결제창(테스트 키, 실제 청구 없음).
 *
 * 앱은 결제 결과를 스스로 판단하지 않는다. 결제창에서 돌아오면 서버가 승인한 결과만 조회해 저장한다.
 */
interface PaymentRemoteDataSource {
    /** 서버에 주문을 만든다. 받은 주문 번호를 기억해 두면 앱으로 돌아오지 못해도 결과를 확인할 수 있다. */
    suspend fun createOrder(productId: String): CreateOrderResponseDto

    /** 서버가 승인한 주문 결과 */
    suspend fun fetchResult(orderId: String): PaymentResultDto
}

class TossTestPaymentRemoteDataSource @Inject constructor(
    private val api: RouteAlarmProxyApi,
) : PaymentRemoteDataSource {
    override suspend fun createOrder(productId: String): CreateOrderResponseDto =
        api.createOrder(CreateOrderRequestDto(productId))

    override suspend fun fetchResult(orderId: String): PaymentResultDto = api.getPayment(orderId)
}
