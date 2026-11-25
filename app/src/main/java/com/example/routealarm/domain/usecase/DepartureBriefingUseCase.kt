package com.example.routealarm.domain.usecase

import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.repository.ScheduleRepository
import javax.inject.Inject

/** 출발 알림에 붙일 첫 탑승 정보: "다산역 8호선 · 약 8분 후 도착" */
data class BoardingInfo(
    val stationName: String,
    val lineName: String?,
    /** 서버가 실시간 도착 정보로 만든 안내. 배차 간격으로 추정했으면 "약"이 붙어 있다. */
    val arrivalInfo: String,
)

/** 첫 탑승 구간에 실시간 도착 안내가 있으면 돌려준다. 시간표 기준 경로면 null */
fun AlarmPlan.boardingInfo(): BoardingInfo? {
    val firstRide = route?.segments?.firstOrNull { it.type != SegmentType.WALK } ?: return null
    val info = firstRide.arrivalInfo?.takeIf { it.isNotBlank() } ?: return null
    return BoardingInfo(stationName = firstRide.startName, lineName = firstRide.lineName, arrivalInfo = info)
}

/**
 * 출발 직전 확인.
 *
 * 서버는 출발이 30분 이내일 때만 실시간 도착 정보를 붙인다. 정기 재확인(기상 24시간~20분 전)은 그보다 이르므로,
 * 출발 알림(출발 10분 전·출발 시각)이 울릴 때 경로를 한 번 더 조회해 그 순간의 도착 정보를 알림에 담는다.
 *
 * 이 시점에는 기상 알람이 이미 울렸으므로 [RefreshTransitTimeUseCase] 는 알람을 옮기지 않고
 * 예상 도착·경로 정보만 최신으로 바꾼다. 조회에 실패해도 마지막 계산값이 그대로 남아 알림은 정상으로 나간다.
 */
class DepartureBriefingUseCase @Inject constructor(
    private val refreshTransitTime: RefreshTransitTimeUseCase,
    private val scheduleRepository: ScheduleRepository,
) {
    suspend operator fun invoke(scheduleId: Long): Schedule? {
        refreshTransitTime(scheduleId)
        return scheduleRepository.getSchedule(scheduleId)
    }
}
