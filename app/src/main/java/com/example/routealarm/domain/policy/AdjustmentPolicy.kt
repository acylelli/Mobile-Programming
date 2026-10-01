package com.example.routealarm.domain.policy

/**
 * 알람 재조정 기준값(Config).
 *
 * 설계 이유: 교통 API 의 ETA 는 호출할 때마다 1~3분씩 흔들린다. 매번 알람을 바꾸면
 * 사용자는 알람을 신뢰하지 못하게 되므로 히스테리시스(최소 변화량)를 둔다.
 *
 * - 앞당기기(지각 위험)는 [minimumAdjustmentMinutes] 이상이면 반영한다.
 * - 늦추기(늦잠 위험)는 [minimumPostponeMinutes] 이상일 때만 반영한다. 교통 상황이 다시 나빠질 수 있으므로
 *   "일찍 깨우는 쪽"으로 비대칭하게 보수적으로 동작한다.
 * - [maximumAutomaticAdjustmentMinutes] 를 넘는 급격한 변화는 API 오류일 가능성도 있으므로
 *   자동 변경하지 않고 사용자 확인을 받는다.
 * - 단, 기존 알람까지 [confirmationWindowMinutes] 이내로 남았다면 사용자는 자고 있어 확인할 수 없다.
 *   이때 "앞당기기"는 지각을 막기 위해 자동으로 반영한다(안전 우선 예외).
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
