package com.example.routealarm.shared.subscription

import com.example.routealarm.shared.api.GroupPassDto
import com.example.routealarm.shared.api.ProxyClient
import com.example.routealarm.shared.api.ProxyException
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class PlanType { FREE, PRO_MONTHLY, PRO_YEARLY, GROUP_PASS }

/** PRO 기간제 이용권(월간 30일, 연간 365일). 금액·기간은 Proxy 서버(server/src/payments.ts)와 같아야 한다(서버 값이 기준). */
enum class SubscriptionProduct(val productId: String, val priceWon: Int, val days: Int) {
    YEARLY("routealarm_pro_yearly", 24_000, 365),
    MONTHLY("routealarm_pro_monthly", 2_900, 30),
    ;

    companion object {
        fun of(productId: String): SubscriptionProduct? = entries.firstOrNull { it.productId == productId }
    }
}

/**
 * 마지막으로 확인된 구독 상태. PRO 여부를 Boolean 하나로 두지 않고 "어떤 플랜이 언제까지"로 저장한다.
 * 시각은 에포크 밀리초(안드로이드 DataStore·iOS UserDefaults 에 그대로 저장한다).
 */
data class Entitlement(
    val plan: PlanType = PlanType.FREE,
    val startedAtMillis: Long? = null,
    val trialEndsAtMillis: Long? = null,
    /** 개인 구독: 다음 결제일, 단체 이용권: 이용 종료 시각 */
    val expiresAtMillis: Long? = null,
    val autoRenew: Boolean = false,
    val organization: String? = null,
    val lastVerifiedAtMillis: Long? = null,
) {
    val isPersonalSubscription: Boolean get() = plan == PlanType.PRO_MONTHLY || plan == PlanType.PRO_YEARLY

    fun isPro(nowMillis: Long): Boolean = when (plan) {
        PlanType.FREE -> false
        // 자동 갱신 중인 구독은 다음 결제일이 지나도 서버가 해지를 알려주기 전까지 유지한다.
        PlanType.PRO_MONTHLY, PlanType.PRO_YEARLY -> autoRenew || expiresAtMillis?.let { it > nowMillis } != false
        PlanType.GROUP_PASS -> expiresAtMillis?.let { it > nowMillis } != false
    }

    fun isInTrial(nowMillis: Long): Boolean = isPersonalSubscription && isPro(nowMillis) && trialEndsAtMillis?.let { it > nowMillis } == true

    /** 설정·구독 관리에 보이는 플랜 이름 */
    fun planName(nowMillis: Long): String = when {
        !isPro(nowMillis) -> "무료 플랜"
        plan == PlanType.PRO_YEARLY -> "PRO 연간"
        plan == PlanType.PRO_MONTHLY -> "PRO 월간"
        else -> "단체 이용권"
    }

    companion object {
        val Free = Entitlement()
    }
}

/** 서버가 확인해 준 단체 이용권. 날짜는 ISO(yyyy-MM-dd) */
data class GroupPass(
    val code: String,
    val organization: String,
    val validFrom: LocalDate,
    val validUntil: LocalDate,
    val remainingSeats: Int,
) {
    /** "2026.03.01 ~ 2027.02.28" */
    val periodText: String get() = "${validFrom.dotted()} ~ ${validUntil.dotted()}"
}

fun LocalDate.dotted(): String =
    "$year.${(month.ordinal + 1).toString().padStart(2, '0')}.${day.toString().padStart(2, '0')}"

object PlanLimits {
    /** 무료 플랜에서 만들 수 있는 일정 수 */
    const val FREE_SCHEDULE_LIMIT = 2

    /** 연간 결제를 12개월로 나눈 금액과, 월간 결제 대비 할인율(화면 표시용) */
    val YEARLY_MONTHLY_EQUIVALENT_WON: Int = SubscriptionProduct.YEARLY.priceWon / 12
    val YEARLY_DISCOUNT_PERCENT: Int = (SubscriptionProduct.MONTHLY.priceWon * 12).let { yearOfMonthly ->
        (yearOfMonthly - SubscriptionProduct.YEARLY.priceWon) * 100 / yearOfMonthly
    }

    /** 새 일정을 만들 수 있는지: PRO 는 무제한, 무료는 [FREE_SCHEDULE_LIMIT]개까지 */
    fun reached(used: Int, isPro: Boolean): Boolean = !isPro && used >= FREE_SCHEDULE_LIMIT
}

/** 단체 이용권 코드 규칙. `HSU-2026-WAKE7` 처럼 영문 대문자·숫자를 하이픈으로 묶은 형태 */
object GroupPassCode {
    const val MAX_LENGTH = 20
    private val COMPLETE = Regex("^[A-Z0-9]{2,6}-[0-9]{4}-[A-Z0-9]{4,8}$")

    /** 입력창에 남길 글자만 거른다: 영문·숫자·하이픈. 대소문자는 건드리지 않는다. */
    fun sanitize(raw: String): String =
        raw.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' }.take(MAX_LENGTH)

    /** 서버에 보내는 형태: 허용 문자만 남기고 영문은 대문자로 바꾼다. */
    fun normalize(raw: String): String = sanitize(raw).uppercase()

    /** 서버에 물어볼 만큼 완성된 코드인지 */
    fun isComplete(code: String): Boolean = COMPLETE.matches(code)
}

/** 화면에 보여줄 구독 오류(안드로이드 AppError 와 같은 문구) */
enum class SubscriptionError(val message: String) {
    GROUP_PASS_INVALID("등록되지 않은 코드예요. 기관에서 받은 코드를 다시 확인해 주세요."),
    GROUP_PASS_EXPIRED("이용 기간이 끝난 이용권이에요."),
    GROUP_PASS_FULL("기관이 구매한 인원이 모두 사용됐어요. 기관에 문의해 주세요."),
    PURCHASE_FAILED("결제를 시작하지 못했어요. 잠시 후 다시 시도해 주세요."),
    NETWORK("인터넷 연결을 확인해 주세요."),
    TOO_MANY_REQUESTS("지금 요청이 많아 잠시 쉬고 있어요. 조금 뒤에 다시 시도해 주세요."),
    SERVER("교통 정보 서버에 문제가 있어요. 잠시 후 다시 시도해 주세요."),
}

sealed class CheckoutOutcome {
    data class Ready(val orderId: String, val checkoutUrl: String) : CheckoutOutcome()
    data class Failed(val error: SubscriptionError) : CheckoutOutcome()
}

sealed class PaymentOutcome {
    /** 서버가 승인한 주문: 이 플랜을 저장한다 */
    data class Paid(val entitlement: Entitlement) : PaymentOutcome()

    /** 아직 결제 중(READY): 주문을 남겨 두고 나중에 다시 확인한다 */
    data object Pending : PaymentOutcome()

    /** 실패로 끝난 주문: 더 확인하지 않는다 */
    data object Declined : PaymentOutcome()
    data class Failed(val error: SubscriptionError) : PaymentOutcome()
}

sealed class GroupPassOutcome {
    data class Verified(val pass: GroupPass) : GroupPassOutcome()
    data class Applied(val entitlement: Entitlement) : GroupPassOutcome()
    data class Failed(val error: SubscriptionError) : GroupPassOutcome()
}

/**
 * PRO 결제·단체 이용권(안드로이드 DefaultSubscriptionRepository 와 같은 규칙).
 * 앱은 결제 결과를 스스로 판단하지 않는다. 서버가 승인한 결과만 Entitlement 로 바꾼다.
 */
class SubscriptionService(
    private val client: ProxyClient,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    /** iOS: 기기 시간대를 쓴다(Swift 에는 기본 인자가 없다) */
    constructor(client: ProxyClient) : this(client, TimeZone.currentSystemDefault())

    suspend fun startCheckout(product: SubscriptionProduct, platform: String? = null): CheckoutOutcome = try {
        client.createOrder(product.productId, platform).let { CheckoutOutcome.Ready(it.orderId, it.checkoutUrl) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CheckoutOutcome.Failed(if (e is ProxyException) e.toError(SubscriptionError.PURCHASE_FAILED) else SubscriptionError.PURCHASE_FAILED)
    }

    suspend fun completePayment(orderId: String, nowMillis: Long): PaymentOutcome = try {
        val result = client.payment(orderId)
        val status = result.subscription
        val product = status?.let { SubscriptionProduct.of(it.productId) }
        val expires = status?.let { parseMillis(it.expiresAt) }
        when {
            result.status == "FAILED" -> PaymentOutcome.Declined
            result.status != "PAID" || product == null || expires == null -> PaymentOutcome.Pending
            else -> PaymentOutcome.Paid(
                Entitlement(
                    plan = if (product == SubscriptionProduct.YEARLY) PlanType.PRO_YEARLY else PlanType.PRO_MONTHLY,
                    startedAtMillis = parseMillis(status.startedAt) ?: nowMillis,
                    expiresAtMillis = expires,
                    // 기간제 이용권: 자동 갱신 없이 결제한 기간만 PRO
                    autoRenew = false,
                    lastVerifiedAtMillis = nowMillis,
                ),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PaymentOutcome.Failed(if (e is ProxyException) e.toError(SubscriptionError.PURCHASE_FAILED) else SubscriptionError.PURCHASE_FAILED)
    }

    /** 코드 확인만(사용하지 않음). 형식이 틀린 코드는 서버에 보내지 않는다. */
    suspend fun verifyGroupPass(rawCode: String, nowMillis: Long): GroupPassOutcome {
        val code = GroupPassCode.normalize(rawCode)
        if (!GroupPassCode.isComplete(code)) return GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_INVALID)
        return groupPassCall { checked(client.groupPass(code), nowMillis) }
    }

    /** 사용 처리 전에 한 번 더 확인하고, 이용 기간의 마지막 날 자정까지 PRO 를 준다. */
    suspend fun redeemGroupPass(rawCode: String, nowMillis: Long): GroupPassOutcome {
        val code = GroupPassCode.normalize(rawCode)
        if (!GroupPassCode.isComplete(code)) return GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_INVALID)
        return groupPassCall {
            val verified = checked(client.groupPass(code), nowMillis)
            if (verified !is GroupPassOutcome.Verified) return@groupPassCall verified
            val pass = client.redeemGroupPass(code).toDomain() ?: return@groupPassCall GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_INVALID)
            GroupPassOutcome.Applied(
                Entitlement(
                    plan = PlanType.GROUP_PASS,
                    startedAtMillis = nowMillis,
                    expiresAtMillis = pass.validUntil.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds(),
                    autoRenew = false,
                    organization = pass.organization,
                    lastVerifiedAtMillis = nowMillis,
                ),
            )
        }
    }

    private fun checked(dto: GroupPassDto, nowMillis: Long): GroupPassOutcome {
        val pass = dto.toDomain() ?: return GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_INVALID)
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
        return when {
            pass.validUntil < today -> GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_EXPIRED)
            pass.remainingSeats <= 0 -> GroupPassOutcome.Failed(SubscriptionError.GROUP_PASS_FULL)
            else -> GroupPassOutcome.Verified(pass)
        }
    }

    private suspend fun groupPassCall(block: suspend () -> GroupPassOutcome): GroupPassOutcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ProxyException) {
        GroupPassOutcome.Failed(if (e.status == 404) SubscriptionError.GROUP_PASS_INVALID else e.toError(SubscriptionError.SERVER))
    } catch (e: Exception) {
        GroupPassOutcome.Failed(SubscriptionError.SERVER)
    }

    private fun ProxyException.toError(fallback: SubscriptionError) = when (status) {
        0 -> SubscriptionError.NETWORK
        429 -> SubscriptionError.TOO_MANY_REQUESTS
        else -> fallback
    }

    private fun GroupPassDto.toDomain(): GroupPass? {
        val from = runCatching { LocalDate.parse(validFrom) }.getOrNull() ?: return null
        val until = runCatching { LocalDate.parse(validUntil) }.getOrNull() ?: return null
        return GroupPass(code, organization, from, until, remainingSeats)
    }

    private fun parseMillis(value: String): Long? = runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()

    companion object {
        /** 서버는 주문을 하루만 보관한다. 그보다 오래된 주문은 더 확인하지 않는다. */
        const val PENDING_ORDER_TTL_MILLIS: Long = 24 * 60 * 60 * 1000L
    }
}
