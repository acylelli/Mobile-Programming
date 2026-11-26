package com.example.routealarm.domain.usecase

import com.example.routealarm.domain.policy.AdjustmentDecision
import com.example.routealarm.domain.policy.AdjustmentPolicy
import com.example.routealarm.shared.AdjustmentEngine
import java.time.Instant
import javax.inject.Inject
import kotlin.time.toKotlinInstant
import com.example.routealarm.shared.AdjustmentDecision as SharedDecision
import com.example.routealarm.shared.AdjustmentPolicy as SharedPolicy

/**
 * 새로 계산된 기상 시각을 실제 알람에 반영할지 결정한다. 규칙은 [AdjustmentPolicy] 참고.
 * 판단은 shared 모듈의 [AdjustmentEngine] 이 한다.
 */
class EvaluateAlarmAdjustmentUseCase @Inject constructor() {

    operator fun invoke(
        currentWakeUp: Instant,
        proposedWakeUp: Instant,
        autoAdjustEnabled: Boolean,
        policy: AdjustmentPolicy = AdjustmentPolicy.Default,
        now: Instant? = null,
    ): AdjustmentDecision {
        val decision = AdjustmentEngine.evaluate(
            currentWakeUp = currentWakeUp.toKotlinInstant(),
            proposedWakeUp = proposedWakeUp.toKotlinInstant(),
            autoAdjustEnabled = autoAdjustEnabled,
            policy = SharedPolicy(
                minimumAdjustmentMinutes = policy.minimumAdjustmentMinutes,
                minimumPostponeMinutes = policy.minimumPostponeMinutes,
                maximumAutomaticAdjustmentMinutes = policy.maximumAutomaticAdjustmentMinutes,
                confirmationWindowMinutes = policy.confirmationWindowMinutes,
            ),
            now = now?.toKotlinInstant(),
        )
        return when (decision) {
            is SharedDecision.Keep -> AdjustmentDecision.Keep(decision.deltaMinutes)
            is SharedDecision.AutoAdjust -> AdjustmentDecision.AutoAdjust(decision.deltaMinutes)
            is SharedDecision.Suggest -> AdjustmentDecision.Suggest(decision.deltaMinutes)
            is SharedDecision.RequireConfirmation -> AdjustmentDecision.RequireConfirmation(decision.deltaMinutes)
        }
    }
}
