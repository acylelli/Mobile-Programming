package com.example.routealarm.domain.policy

import java.time.Duration
import java.time.Instant

/**
 * 교통 정보를 언제 다시 확인할지 결정한다.
 *
 * 설계 이유(배터리): 몇 분마다 서버를 호출하지 않는다. 알람이 멀리 있으면 하루 전 한 번,
 * 가까워질수록 촘촘하게(3시간 전 → 1시간 전 → 20분 전) 확인한다.
 * 한 일정당 하루 최대 4회 호출이며, 마지막 확인 이후에는 AlarmManager 알람만 남는다.
 */
object RefreshCheckpointPolicy {
    val CHECKPOINTS_BEFORE_WAKE_UP: List<Duration> = listOf(
        Duration.ofHours(24),
        Duration.ofHours(3),
        Duration.ofMinutes(60),
        Duration.ofMinutes(20),
    )

    /** 지금 바로 실행되어 버리는 너무 가까운 체크포인트는 건너뛴다. */
    private val MIN_LEAD: Duration = Duration.ofMinutes(1)

    fun nextCheckpoint(wakeUp: Instant, now: Instant): Instant? =
        CHECKPOINTS_BEFORE_WAKE_UP
            .map { wakeUp.minus(it) }
            .filter { it.isAfter(now.plus(MIN_LEAD)) }
            .minOrNull()
}
