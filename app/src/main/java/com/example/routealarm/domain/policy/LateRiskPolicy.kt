package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmPlan
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** 지각 위험: 대중교통으로 가면 목표 도착보다 늦는다 */
data class LateRisk(
    val reason: Reason,
    /** 이 회차의 목표 도착 */
    val target: Instant,
    /** 출발하게 될 시각(교통 악화: 계획된 출발, 늦잠: 알람을 끈 시각 + 준비 시간) */
    val leaveAt: Instant,
    /** 대중교통 도착 예상 */
    val transitArrival: Instant,
    /** 목표보다 늦는 시간(분) */
    val lateMinutes: Int,
) {
    enum class Reason {
        /** 출발 전인데 교통이 나빠져 계획대로 나가도 늦는다 */
        TRAFFIC,

        /** 기상 알람을 늦게 꺼서 준비 후 나가면 늦는다 */
        OVERSLEPT,
    }
}

/**
 * 지각 위험 판정(택시 안내를 띄울지).
 *
 * 설계 이유: "지금 시각 + 이동시간"으로만 판단하면 제때 나가서 이동 중인 사람에게도 매번 경고가 뜬다.
 * 그래서 사용자가 늦을 수밖에 없는 근거가 있을 때만 알린다.
 * - 교통 악화: 아직 출발 전인데 계산된 도착 예상이 이미 목표보다 늦다.
 * - 늦잠: 이 회차 기상 알람을 끈 시각 + 준비 시간에 나가면 목표보다 늦는다(실제로 끈 시각이 근거).
 *   출발 알림 뒤에는 계획이 다음 회차로 넘어가므로, 알람을 끈 기록으로 이 회차의 목표 시각을 되짚는다.
 * 목표 도착 [WINDOW] 전부터 목표 도착 전까지만 판단한다.
 */
object LateRiskPolicy {
    val WINDOW: Duration = Duration.ofHours(3)

    fun assess(
        plan: AlarmPlan?,
        targetTime: LocalTime,
        preparationMinutes: Int,
        lastDismissal: AlarmHistoryEvent?,
        now: Instant,
        zone: ZoneId,
    ): LateRisk? = overslept(plan, targetTime, preparationMinutes, lastDismissal, now, zone) ?: traffic(plan, now)

    private fun traffic(plan: AlarmPlan?, now: Instant): LateRisk? {
        if (plan == null || !inWindow(plan.targetArrival, now) || now.isAfter(plan.departure)) return null
        val late = Duration.between(plan.targetArrival, plan.estimatedArrival).toMinutes()
        return if (late > 0) LateRisk(LateRisk.Reason.TRAFFIC, plan.targetArrival, plan.departure, plan.estimatedArrival, late.toInt()) else null
    }

    private fun overslept(
        plan: AlarmPlan?,
        targetTime: LocalTime,
        preparationMinutes: Int,
        dismissal: AlarmHistoryEvent?,
        now: Instant,
        zone: ZoneId,
    ): LateRisk? {
        if (plan == null || dismissal == null || dismissal.type != AlarmEventType.DISMISSED) return null
        val plannedWake = dismissal.newWakeUp ?: return null
        // 이 회차의 목표 도착: 예정 기상 이후 처음 오는 목표 시각
        val wakeLocal = plannedWake.atZone(zone)
        var target = wakeLocal.toLocalDate().atTime(targetTime).atZone(zone).toInstant()
        if (!target.isAfter(plannedWake)) target = target.plus(Duration.ofDays(1))
        if (!inWindow(target, now) || dismissal.occurredAt.isAfter(target)) return null

        val leaveAt = dismissal.occurredAt.plus(Duration.ofMinutes(preparationMinutes.toLong()))
        val transitArrival = leaveAt.plus(Duration.ofMinutes(plan.travelMinutes.toLong()))
        val late = Duration.between(target, transitArrival).toMinutes()
        return if (late > 0) LateRisk(LateRisk.Reason.OVERSLEPT, target, leaveAt, transitArrival, late.toInt()) else null
    }

    private fun inWindow(target: Instant, now: Instant) = now.isBefore(target) && !now.isBefore(target.minus(WINDOW))

    /** 택시 도착 예상: 출발하게 될 시각(이미 지났으면 지금)부터 택시 소요 시간 뒤 */
    fun taxiArrival(risk: LateRisk, now: Instant, taxiMinutes: Int): Instant =
        maxOf(now, risk.leaveAt).plus(Duration.ofMinutes(taxiMinutes.toLong()))
}
