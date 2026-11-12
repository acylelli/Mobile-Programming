package com.example.routealarm.domain.model

import java.time.Instant
import java.time.LocalDate

enum class PlanType { FREE, PRO_MONTHLY, PRO_YEARLY, GROUP_PASS }

/** Google Play 정기 결제 상품(INR-003). 금액은 화면 표시용이며 실제 청구는 Play 가 처리한다. */
enum class SubscriptionProduct(val productId: String, val priceWon: Int) {
    YEARLY("routealarm_pro_yearly", 24_000),
    MONTHLY("routealarm_pro_monthly", 2_900),
    ;

    companion object {
        /** 첫 구독에 제공하는 무료체험 기간 */
        const val TRIAL_DAYS = 7L
    }
}

/**
 * 마지막으로 확인된 구독 상태(DAR-001). DataStore 에 저장되어 네트워크가 없어도 그대로 유지된다.
 *
 * 설계 이유: PRO 여부를 Boolean 하나로 두지 않고 "어떤 플랜이 언제까지"로 저장한다.
 * 해지하면 결제 기간이 끝나는 시점에 자동으로 무료 플랜이 되고(일정 데이터는 건드리지 않는다),
 * 단체 이용권은 기관이 정한 이용 기간이 끝나면 만료된다.
 */
data class Entitlement(
    val plan: PlanType = PlanType.FREE,
    val startedAt: Instant? = null,
    /** 무료체험이 끝나고 첫 결제가 이루어지는 시각 */
    val trialEndsAt: Instant? = null,
    /** 개인 구독: 다음 결제일, 단체 이용권: 이용 종료 시각 */
    val expiresAt: Instant? = null,
    val autoRenew: Boolean = false,
    /** 단체 이용권을 제공한 기관 이름 */
    val organization: String? = null,
    val lastVerifiedAt: Instant? = null,
) {
    val isPersonalSubscription: Boolean
        get() = plan == PlanType.PRO_MONTHLY || plan == PlanType.PRO_YEARLY

    fun isPro(now: Instant): Boolean = when (plan) {
        PlanType.FREE -> false
        // 자동 갱신 중인 구독은 다음 결제일이 지나도 서버가 해지를 알려주기 전까지 유지한다.
        PlanType.PRO_MONTHLY, PlanType.PRO_YEARLY -> autoRenew || expiresAt?.isAfter(now) != false
        PlanType.GROUP_PASS -> expiresAt?.isAfter(now) != false
    }

    fun isInTrial(now: Instant): Boolean = isPersonalSubscription && isPro(now) && trialEndsAt?.isAfter(now) == true

    companion object {
        val Free = Entitlement()
    }
}

/** 서버가 확인해 준 단체 이용권(SFR-016). 개인 위치·일정 정보와는 무관하다. */
data class GroupPass(
    val code: String,
    val organization: String,
    val validFrom: LocalDate,
    val validUntil: LocalDate,
    val remainingSeats: Int,
)

object PlanLimits {
    /** 무료 플랜에서 만들 수 있는 일정 수 */
    const val FREE_SCHEDULE_LIMIT = 2

    /** 연간 결제를 12개월로 나눈 금액과, 월간 결제 대비 할인율(화면 표시용) */
    val YEARLY_MONTHLY_EQUIVALENT_WON: Int = SubscriptionProduct.YEARLY.priceWon / 12
    val YEARLY_DISCOUNT_PERCENT: Int = (SubscriptionProduct.MONTHLY.priceWon * 12).let { yearOfMonthly ->
        (yearOfMonthly - SubscriptionProduct.YEARLY.priceWon) * 100 / yearOfMonthly
    }
}

/** 무료 플랜의 일정 사용량. PRO 는 제한이 없다. */
sealed interface ScheduleQuota {
    data object Unlimited : ScheduleQuota

    data class Limited(val used: Int, val limit: Int) : ScheduleQuota {
        val reached: Boolean get() = used >= limit
    }
}
