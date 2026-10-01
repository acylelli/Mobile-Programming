package com.example.routealarm.domain.repository

import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.model.Schedule
import java.time.Instant

/**
 * Android AlarmManager 를 Domain 에서 숨기는 인터페이스.
 * UseCase 는 "언제 무엇을 울릴지"만 결정하고, 실제 등록 방식(setAlarmClock 등)은 구현체가 책임진다.
 * 덕분에 알람 스케줄링 규칙을 JVM 단위 테스트로 검증할 수 있다.
 */
interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean

    /** @return 정확한 알람으로 등록되었으면 true, 권한이 없어 근사 알람으로 대체되면 false */
    fun schedule(scheduleId: Long, type: AlarmType, triggerAt: Instant): Boolean

    fun cancel(scheduleId: Long, types: Collection<AlarmType> = AlarmType.entries)
}

/**
 * WorkManager 기반 교통 정보 재확인 예약. WorkManager 는 정확한 시각 실행을 보장하지 않으므로
 * 여기서는 "대략 그 즈음 다시 확인"만 담당하고 실제 알람은 [AlarmScheduler] 가 담당한다.
 */
interface TransitRefreshScheduler {
    fun scheduleRefresh(scheduleId: Long, runAt: Instant)
    fun cancelRefresh(scheduleId: Long)
}

/** 사용자에게 보내는 교통/알람 변경 알림 */
interface AlarmNotifier {
    /** @param isRealtime false 면 실시간 조회 실패 후 캐시된 경로로 다시 계산한 결과 */
    fun notifyAlarmAdjusted(schedule: Schedule, previousWakeUp: Instant, newWakeUp: Instant, isRealtime: Boolean)
    fun notifyAdjustmentSuggested(schedule: Schedule, deltaMinutes: Long, needsConfirmation: Boolean)
}
