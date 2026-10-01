package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.getOrNull
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.policy.TravelEstimatePolicy
import com.example.routealarm.domain.repository.ScheduleRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * 네트워크 없이 일정을 "다음 회차" 기준으로 맞추고 알람을 다시 등록한다.
 *
 * 사용 시점: 재부팅, 시간대/시스템 시간 변경, 알람 종료 후 다음 회차 준비, 일정 활성화.
 * 설계 이유: BOOT_COMPLETED 리시버는 짧은 시간 안에 끝나야 하고 부팅 직후엔 네트워크가 없을 수 있다.
 * 그래서 Room 에 저장된 마지막 이동시간으로 즉시 알람을 복구하고, 실시간 갱신은 WorkManager 에 맡긴다.
 */
class SyncScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val resolveNextOccurrence: ResolveNextOccurrenceUseCase,
    private val buildAlarmPlan: BuildAlarmPlanUseCase,
    private val scheduleAlarm: ScheduleAlarmUseCase,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(schedule: Schedule): Schedule {
        if (!schedule.enabled) {
            scheduleAlarm(schedule)
            return schedule
        }
        val now = timeProvider.now()
        val target = resolveNextOccurrence(
            targetTime = schedule.targetArrivalTime,
            repeatDays = schedule.repeatDays,
            oneTimeDate = schedule.oneTimeDate,
            now = now,
            zone = timeProvider.zone(),
            leadTime = schedule.occurrenceLeadTime(),
        )

        if (target == null || (!schedule.isRepeating && !target.isAfter(now))) {
            // 1회 일정이 끝났다: 기록은 남기고 비활성화한다.
            val finished = schedule.copy(enabled = false, pendingAdjustment = null, updatedAt = now)
            scheduleRepository.upsert(finished)
            scheduleAlarm(finished)
            return finished
        }

        val currentPlan = schedule.plan
        if (currentPlan != null && currentPlan.targetArrival == target) {
            scheduleAlarm(schedule)
            return schedule
        }

        // 새 회차이거나 시간대가 바뀌어 목표 시각(Instant)이 달라졌다 → 마지막 이동시간으로 오프라인 재계산
        val updated = schedule.copy(
            plan = rebuildOffline(schedule, target, now) ?: currentPlan,
            pendingAdjustment = null,
            updatedAt = now,
        )
        scheduleRepository.upsert(updated)
        scheduleAlarm(updated)
        return updated
    }

    private fun rebuildOffline(schedule: Schedule, target: Instant, now: Instant): AlarmPlan? {
        val last = schedule.plan
        val input = PlanInput(
            targetArrival = target,
            currentTravel = Duration.ofMinutes(
                last?.travelMinutes?.toLong() ?: TravelEstimatePolicy.FALLBACK_TRAVEL_MINUTES,
            ),
            preparation = schedule.preparation,
            baseBufferMinutes = schedule.bufferMinutes,
            isRealtime = false,
            fetchedAt = last?.lastUpdatedAt ?: now,
            route = last?.route,
            previousTravelMinutes = last?.travelMinutes,
        )
        return buildAlarmPlan(input, now).getOrNull()?.plan
    }
}
