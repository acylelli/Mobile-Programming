package com.example.routealarm.presentation.widget

import com.example.routealarm.domain.model.Schedule
import java.time.Instant

/**
 * 위젯에 보여 줄 다음 알람: 켜져 있고 계산이 끝난 일정 중 기상 알람이 가장 이른 것.
 * Android 의존성 없이 단위 테스트한다.
 */
object NextAlarm {
    fun pick(schedules: List<Schedule>, now: Instant): Schedule? =
        schedules
            .filter { it.enabled && it.plan != null }
            // 이미 지난 기상 알람(출발 전)은 출발 시각 기준으로 아직 의미가 있으므로 출발 시각까지는 남긴다.
            .filter { it.plan!!.departure.isAfter(now) }
            .minByOrNull { it.plan!!.wakeUp }
}
