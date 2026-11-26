package com.example.routealarm.shared

import kotlin.math.abs
import kotlin.time.Instant

/**
 * 알람 재조정 기준값. 교통 API 의 ETA 는 호출마다 1~3분씩 흔들리므로 최소 변화량을 둔다.
 * 앞당기기는 5분 이상, 늦추기는 10분 이상일 때만 반영하고(일찍 깨우는 쪽으로 보수적),
 * 30분을 넘는 급격한 변화는 사용자 확인을 받는다. 단 알람까지 60분 이내면 확인할 수 없으므로
 * 앞당기기는 자동으로 반영한다.
 */
data class AdjustmentPolicy(
    val minimumAdjustmentMinutes: Int = 5,
    val minimumPostponeMinutes: Int = 10,
    val maximumAutomaticAdjustmentMinutes: Int = 30,
    val confirmationWindowMinutes: Int = 60,
) {
    companion object {
        val Default = AdjustmentPolicy()
    }
}

sealed interface AdjustmentDecision {
    /** 음수 = 알람을 앞당김, 양수 = 늦춤 */
    val deltaMinutes: Long

    data class Keep(override val deltaMinutes: Long) : AdjustmentDecision
    data class AutoAdjust(override val deltaMinutes: Long) : AdjustmentDecision
    data class Suggest(override val deltaMinutes: Long) : AdjustmentDecision
    data class RequireConfirmation(override val deltaMinutes: Long) : AdjustmentDecision
}

object AdjustmentEngine {
    fun evaluate(
        currentWakeUp: Instant,
        proposedWakeUp: Instant,
        autoAdjustEnabled: Boolean,
        policy: AdjustmentPolicy = AdjustmentPolicy.Default,
        now: Instant? = null,
    ): AdjustmentDecision {
        val delta = (proposedWakeUp - currentWakeUp).inWholeMinutes
        val magnitude = abs(delta)
        val isPostpone = delta > 0
        val userCannotConfirm = now != null && (currentWakeUp - now).inWholeMinutes <= policy.confirmationWindowMinutes
        return when {
            magnitude < policy.minimumAdjustmentMinutes -> AdjustmentDecision.Keep(delta)
            isPostpone && magnitude < policy.minimumPostponeMinutes -> AdjustmentDecision.Keep(delta)
            magnitude > policy.maximumAutomaticAdjustmentMinutes ->
                if (!isPostpone && userCannotConfirm) AdjustmentDecision.AutoAdjust(delta) else AdjustmentDecision.RequireConfirmation(delta)
            !autoAdjustEnabled -> AdjustmentDecision.Suggest(delta)
            else -> AdjustmentDecision.AutoAdjust(delta)
        }
    }
}
