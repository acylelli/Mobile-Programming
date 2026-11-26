package com.example.routealarm.shared

import com.example.routealarm.shared.api.ProxyClient
import com.example.routealarm.shared.subscription.CheckoutOutcome
import com.example.routealarm.shared.subscription.Entitlement
import com.example.routealarm.shared.subscription.GroupPassCode
import com.example.routealarm.shared.subscription.GroupPassOutcome
import com.example.routealarm.shared.subscription.PaymentOutcome
import com.example.routealarm.shared.subscription.PlanLimits
import com.example.routealarm.shared.subscription.PlanType
import com.example.routealarm.shared.subscription.SubscriptionError
import com.example.routealarm.shared.subscription.SubscriptionProduct
import com.example.routealarm.shared.subscription.SubscriptionService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class SubscriptionTest {
    private val seoul = TimeZone.of("Asia/Seoul")
    private val now = Instant.parse("2026-11-27T03:00:00Z").toEpochMilliseconds()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun service(handler: (path: String, method: HttpMethod, body: String) -> Pair<HttpStatusCode, String>): SubscriptionService {
        val engine = MockEngine { request ->
            val body = (request.body as? io.ktor.http.content.OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: ""
            val (status, text) = handler(request.url.encodedPath, request.method, body)
            if (status.value >= 400) respondError(status, text) else respond(text, status, json)
        }
        return SubscriptionService(ProxyClient("https://example.test/", HttpClient(engine)), seoul)
    }

    private val pass = """{"code":"HSU-2026-WAKE7","organization":"한성대학교","valid_from":"2026-03-01","valid_until":"2027-02-28","remaining_seats":12}"""

    @Test
    fun `코드는 영문·숫자·하이픈만 남기고 대문자로 보낸다`() {
        assertEquals("hsu-2026wake7", GroupPassCode.sanitize("hsu-2026_wake7!"))
        assertEquals("HSU-2026-WAKE7", GroupPassCode.normalize("hsu-2026-wake7"))
        assertTrue(GroupPassCode.isComplete("HSU-2026-WAKE7"))
        assertFalse(GroupPassCode.isComplete("HSU-26-WAKE7"))
    }

    @Test
    fun `연간은 월 2000원꼴 31퍼센트 할인이고 무료는 일정 2개까지`() {
        assertEquals(2_000, PlanLimits.YEARLY_MONTHLY_EQUIVALENT_WON)
        assertEquals(31, PlanLimits.YEARLY_DISCOUNT_PERCENT)
        assertFalse(PlanLimits.reached(1, isPro = false))
        assertTrue(PlanLimits.reached(2, isPro = false))
        assertFalse(PlanLimits.reached(9, isPro = true))
    }

    @Test
    fun `iOS 주문은 platform 을 보내고 결제창 주소를 받는다`() = runTest {
        var sent = ""
        val outcome = service { path, method, body ->
            assertEquals("/v1/payments/orders", path)
            assertEquals(HttpMethod.Post, method)
            sent = body
            HttpStatusCode.OK to """{"order_id":"ra_1","checkout_url":"https://example.test/pay/checkout?orderId=ra_1"}"""
        }.startCheckout(SubscriptionProduct.MONTHLY, "ios")
        assertEquals(CheckoutOutcome.Ready("ra_1", "https://example.test/pay/checkout?orderId=ra_1"), outcome)
        assertTrue(sent.contains("\"product_id\":\"routealarm_pro_monthly\"") && sent.contains("\"platform\":\"ios\""), sent)
    }

    @Test
    fun `서버가 승인한 주문만 PRO 를 주고 결제 중이면 다시 확인한다`() = runTest {
        val paid = service { _, _, _ ->
            HttpStatusCode.OK to """{"order_id":"ra_1","status":"PAID","subscription":{"product_id":"routealarm_pro_yearly","started_at":"2026-11-27T03:00:00Z","expires_at":"2027-11-27T03:00:00Z","auto_renew":false}}"""
        }.completePayment("ra_1", now)
        assertIs<PaymentOutcome.Paid>(paid)
        assertEquals(PlanType.PRO_YEARLY, paid.entitlement.plan)
        assertTrue(paid.entitlement.isPro(now))
        assertEquals("PRO 연간", paid.entitlement.planName(now))
        assertFalse(paid.entitlement.isPro(Instant.parse("2027-11-28T00:00:00Z").toEpochMilliseconds()))

        assertEquals(PaymentOutcome.Pending, service { _, _, _ -> HttpStatusCode.OK to """{"order_id":"ra_1","status":"READY"}""" }.completePayment("ra_1", now))
        assertEquals(PaymentOutcome.Declined, service { _, _, _ -> HttpStatusCode.OK to """{"order_id":"ra_1","status":"FAILED"}""" }.completePayment("ra_1", now))
    }

    @Test
    fun `단체 이용권은 확인 후 사용하고 마지막 날 자정까지 PRO`() = runTest {
        val calls = mutableListOf<String>()
        val svc = service { path, method, _ ->
            calls += "${method.value} $path"
            HttpStatusCode.OK to pass
        }
        val verified = svc.verifyGroupPass("hsu-2026-wake7", now)
        assertIs<GroupPassOutcome.Verified>(verified)
        assertEquals("2026.03.01 ~ 2027.02.28", verified.pass.periodText)

        val applied = svc.redeemGroupPass("HSU-2026-WAKE7", now)
        assertIs<GroupPassOutcome.Applied>(applied)
        assertEquals("한성대학교", applied.entitlement.organization)
        assertEquals(Instant.parse("2027-02-28T15:00:00Z").toEpochMilliseconds(), applied.entitlement.expiresAtMillis)
        assertEquals("단체 이용권", applied.entitlement.planName(now))
        assertEquals(listOf("GET /v1/group-passes/HSU-2026-WAKE7", "GET /v1/group-passes/HSU-2026-WAKE7", "POST /v1/group-passes/HSU-2026-WAKE7/redeem"), calls)
    }

    @Test
    fun `없는 코드·만료·정원 초과·형식 오류를 구분한다`() = runTest {
        assertEquals(GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_INVALID), service { _, _, _ -> HttpStatusCode.NotFound to "{}" }.verifyGroupPass("ABC-2026-ZZZZ", now))
        assertEquals(
            GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_EXPIRED),
            service { _, _, _ -> HttpStatusCode.OK to pass.replace("2027-02-28", "2026-02-28") }.verifyGroupPass("HSU-2026-WAKE7", now),
        )
        assertEquals(
            GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_FULL),
            service { _, _, _ -> HttpStatusCode.OK to pass.replace("12", "0") }.verifyGroupPass("HSU-2026-WAKE7", now),
        )
        var called = false
        assertEquals(GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_INVALID), service { _, _, _ -> called = true; HttpStatusCode.OK to pass }.verifyGroupPass("HSU", now))
        assertFalse(called)
    }

    @Test
    fun `무료 플랜은 PRO 가 아니다`() {
        assertFalse(Entitlement.Free.isPro(now))
        assertEquals("무료 플랜", Entitlement.Free.planName(now))
    }
}
