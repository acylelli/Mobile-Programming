package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.GroupPass
import com.example.routealarm.domain.model.PlanLimits
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.ScheduleQuota
import com.example.routealarm.domain.model.SubscriptionProduct
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * 단체 이용권 코드 규칙. 기관에서 받은 코드는 `HSU-2026-WAKE7` 처럼 영문 대문자·숫자를 하이픈으로 묶은 형태다.
 */
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

/** 결제창(토스페이먼츠 테스트 결제)에서 돌아온 주문을 확인해 PRO 를 적용한다. 플랜이 바뀌면 재확인 일정도 다시 잡는다. */
class CompletePaymentUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val restoreAlarms: RestoreAlarmsUseCase,
) {
    suspend operator fun invoke(orderId: String): AppResult<Entitlement> {
        val result = subscriptionRepository.completePayment(orderId)
        if (result is AppResult.Success) restoreAlarms()
        return result
    }
}

/**
 * 결제창으로 보낸 뒤 결과를 확인하지 못한 주문이 있으면 다시 확인한다.
 * 결제를 마치고 "앱으로 돌아가기"를 누르지 않고 브라우저를 닫은 경우에도 앱을 다시 열면 PRO 가 적용된다.
 * @return 확인할 주문이 없으면 null
 */
class ResumePendingPaymentUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val completePayment: CompletePaymentUseCase,
) {
    suspend operator fun invoke(): AppResult<Entitlement>? {
        val orderId = subscriptionRepository.pendingOrderId() ?: return null
        return completePayment(orderId)
    }
}

class VerifyGroupPassUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend operator fun invoke(rawCode: String): AppResult<GroupPass> {
        val code = GroupPassCode.normalize(rawCode)
        // 형식이 틀린 코드는 서버에 보내지 않는다.
        if (!GroupPassCode.isComplete(code)) return AppResult.Failure(AppError.GroupPassInvalid)
        return subscriptionRepository.verifyGroupPass(code)
    }
}

data class ApplyGroupPassResult(
    val entitlement: Entitlement,
    /** 자동 갱신되는 개인 구독 중이었다면 해지를 안내해야 한다(SFR-016 중복 처리). */
    val hadPersonalSubscription: Boolean,
)

class ApplyGroupPassUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val restoreAlarms: RestoreAlarmsUseCase,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(rawCode: String): AppResult<ApplyGroupPassResult> {
        val code = GroupPassCode.normalize(rawCode)
        if (!GroupPassCode.isComplete(code)) return AppResult.Failure(AppError.GroupPassInvalid)
        val previous = subscriptionRepository.current()
        // 자동 갱신되는 개인 구독만 해지 안내가 필요하다. 기간제 이용권(테스트 결제)은 기간이 끝나면 저절로 끝난다.
        val hadPersonal = previous.isPersonalSubscription && previous.autoRenew && previous.isPro(timeProvider.now())
        return when (val result = subscriptionRepository.redeemGroupPass(code)) {
            is AppResult.Success -> {
                restoreAlarms()
                AppResult.Success(ApplyGroupPassResult(result.data, hadPersonal))
            }
            is AppResult.Failure -> result
        }
    }
}

/** 무료 플랜으로 되돌린다(구독 종료·시연 초기화). 일정은 그대로 두고 교통 재확인만 무료 기준으로 줄인다. */
class ResetSubscriptionUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val restoreAlarms: RestoreAlarmsUseCase,
) {
    suspend operator fun invoke() {
        subscriptionRepository.clear()
        restoreAlarms()
    }
}

/**
 * 새 일정을 만들 수 있는지(무료 플랜은 [PlanLimits.FREE_SCHEDULE_LIMIT]개까지) 판단한다.
 * 첫 실행 예시 일정은 사용자 데이터가 아니므로 세지 않는다.
 */
class CheckScheduleQuotaUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(): ScheduleQuota = evaluate(
        schedules = scheduleRepository.observeSchedules().first(),
        entitlement = subscriptionRepository.current(),
        now = timeProvider.now(),
    )

    fun evaluate(schedules: List<Schedule>, entitlement: Entitlement, now: Instant): ScheduleQuota =
        if (entitlement.isPro(now)) {
            ScheduleQuota.Unlimited
        } else {
            ScheduleQuota.Limited(used = schedules.size, limit = PlanLimits.FREE_SCHEDULE_LIMIT)
        }
}
