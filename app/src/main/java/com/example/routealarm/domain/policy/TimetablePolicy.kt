package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.TimetableAlarmProposal
import com.example.routealarm.domain.model.TimetableClass
import java.time.DayOfWeek

/**
 * 시간표 → 알람 제안.
 *
 * 설계 이유: 기상 알람은 하루에 한 번, 그날 첫 수업에 맞추면 된다. 수업마다 알람을 만들면 같은 날 여러 번 울린다.
 * 그래서 요일마다 가장 이른 수업만 고르고, 첫 수업 시각이 같은 요일끼리 하나의 반복 일정으로 묶는다.
 */
object TimetablePolicy {

    /** 요일별 첫 수업(가장 이른 시작). 수업이 없는 요일은 빠진다. */
    fun firstClassByDay(classes: List<TimetableClass>): Map<DayOfWeek, TimetableClass> =
        classes.groupBy { it.day }
            .mapValues { (_, list) -> list.minBy { it.start } }
            .toSortedMap()

    /** 첫 수업 시각이 같은 요일끼리 묶은 알람 제안. 이른 시각부터 정렬한다. */
    fun proposals(classes: List<TimetableClass>): List<TimetableAlarmProposal> =
        firstClassByDay(classes).entries
            .groupBy { it.value.start }
            .map { (start, entries) ->
                TimetableAlarmProposal(
                    arrival = start,
                    days = entries.map { it.key }.toSet(),
                    firstClassNames = entries.sortedBy { it.key }.map { it.value.name },
                )
            }
            .sortedWith(compareBy({ it.arrival }, { it.days.min() }))

    /**
     * 시간표로 알람을 다시 만든 뒤 예전 시간표 알람을 어떻게 할지.
     * 새 알람이 모두 만들어졌을 때만 예전 알람을 지운다. 하나라도 실패하면 그 요일 알람이 사라지지 않도록
     * 예전 알람을 남겨 두고, 다음에 다시 만들 때 함께 바뀌도록 둘 다 기억한다.
     */
    fun alarmReplacement(previous: List<Long>, created: List<Long>, anyFailed: Boolean): TimetableAlarmReplacement =
        if (created.isEmpty() || anyFailed) {
            TimetableAlarmReplacement(delete = emptyList(), remember = (previous + created).distinct())
        } else {
            TimetableAlarmReplacement(delete = previous.filterNot { it in created }, remember = created)
        }
}

/** [TimetablePolicy.alarmReplacement] 결과: 지울 예전 알람과, 시간표 알람으로 기억해 둘 알람 */
data class TimetableAlarmReplacement(val delete: List<Long>, val remember: List<Long>)
