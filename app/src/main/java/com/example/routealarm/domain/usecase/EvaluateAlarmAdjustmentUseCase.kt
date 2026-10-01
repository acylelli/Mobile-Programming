package com.example.routealarm.domain.usecase

import com.example.routealarm.domain.policy.AdjustmentDecision
import com.example.routealarm.domain.policy.AdjustmentPolicy
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

/**
 * 새로 계산된 기상 시각을 실제 알람에 반영할지 결정한다. 규칙은 [AdjustmentPolicy] 참고.
 */
class EvaluateAlarmAdjustmentUseCase @Inject constructor() {

    operator fun invoke(
        currentWakeUp: Instant,
        proposedWakeUp: Instant,
        autoAdjustEnabled: Boolean,
        policy: AdjustmentPolicy = AdjustmentPolicy.Default,
        now: Instant? = null,
    ): AdjustmentDecision {
        val delta = Duration.between(currentWakeUp, proposedWakeUp).toMinutes()
        val magnitude = abs(delta)
        val isPostpone = delta > 0
        val userCannotConfirm = now != null &&
            Duration.between(now, currentWakeUp).toMinutes() <= policy.confirmationWindowMinutes

        return when {
            magnitude < policy.minimumAdjustmentMinutes -> AdjustmentDecision.Keep(delta)
            isPostpone && magnitude < policy.minimumPostponeMinutes -> AdjustmentDecision.Keep(delta)
            magnitude > policy.maximumAutomaticAdjustmentMinutes ->
                if (!isPostpone && userCannotConfirm) {
                    AdjustmentDecision.AutoAdjust(delta)
                } else {
                    AdjustmentDecision.RequireConfirmation(delta)
                }

            !autoAdjustEnabled -> AdjustmentDecision.Suggest(delta)
            else -> AdjustmentDecision.AutoAdjust(delta)
        }
    }
}
