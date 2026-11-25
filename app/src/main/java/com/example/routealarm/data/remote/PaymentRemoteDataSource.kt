package com.example.routealarm.data.remote

import android.net.Uri
import com.example.routealarm.BuildConfig
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
    /** 브라우저로 열 결제창 주소 */
    fun checkoutUrl(productId: String): String

    /** 서버가 승인한 주문 결과 */
    suspend fun fetchResult(orderId: String): PaymentResultDto
}

class TossTestPaymentRemoteDataSource @Inject constructor(
    private val api: RouteAlarmProxyApi,
) : PaymentRemoteDataSource {
    override fun checkoutUrl(productId: String): String =
        Uri.parse(BuildConfig.PROXY_BASE_URL).buildUpon()
            .appendEncodedPath("pay/checkout")
            .appendQueryParameter("product", productId)
            .build()
            .toString()

    override suspend fun fetchResult(orderId: String): PaymentResultDto = api.getPayment(orderId)
}
