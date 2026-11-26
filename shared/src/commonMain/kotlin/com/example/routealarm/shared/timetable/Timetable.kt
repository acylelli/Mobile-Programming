package com.example.routealarm.shared.timetable

/** 시간표 수업 하나(안드로이드 TimetableClass). 요일은 월=0 … 일=6, 시각은 하루 안의 분 */
data class TimetableClass(
    val id: String,
    val name: String,
    val day: Int,
    val startMinutes: Int,
    val endMinutes: Int? = null,
    val room: String? = null,
)

/** 시간표로 만들 알람 하나: 첫 수업 시각이 같은 요일들을 묶은 반복 일정(요일 비트마스크 월=1 … 일=64) */
data class TimetableAlarmProposal(val arrivalMinutes: Int, val repeatMask: Int, val dayLabel: String, val firstClassNames: List<String>)

/** 사진 인식 결과(Swift 용): 성공이면 classes, 실패면 error 에 사용자 안내 */
data class TimetableScan(val classes: List<TimetableClass>, val error: String?)

/**
 * 시간표 → 알람 제안(안드로이드 TimetablePolicy 와 같은 규칙).
 * 요일마다 가장 이른 수업만 고르고, 첫 수업 시각이 같은 요일끼리 하나의 반복 일정으로 묶는다.
 */
object Timetables {
    private val LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

    fun proposals(classes: List<TimetableClass>): List<TimetableAlarmProposal> =
        classes.groupBy { it.day }
            .mapValues { (_, list) -> list.minBy { it.startMinutes } }
            .entries
            .groupBy { it.value.startMinutes }
            .map { (start, entries) ->
                val days = entries.map { it.key }.sorted()
                TimetableAlarmProposal(
                    arrivalMinutes = start,
                    repeatMask = days.fold(0) { mask, day -> mask or (1 shl day) },
                    dayLabel = days.joinToString("·") { LABELS[it] },
                    firstClassNames = entries.sortedBy { it.key }.map { it.value.name },
                )
            }
            .sortedWith(compareBy({ it.arrivalMinutes }, { it.repeatMask.countTrailingZeroBits() }))

    /** 글자 인식 결과로 수업을 읽는다(안드로이드 ML Kit 과 iOS Vision 이 같은 해석기를 쓴다) */
    fun scan(lines: List<OcrLine>): TimetableScan = when (val result = TimetableImageParser.parse(lines)) {
        is TimetableParseResult.Success -> TimetableScan(
            result.classes.mapIndexed { index, it ->
                TimetableClass("scan-$index", it.name, it.day.ordinal, it.start.hour * 60 + it.start.minute, null, it.room)
            },
            if (result.classes.isEmpty()) "수업을 찾지 못했어요. 직접 추가해 주세요." else null,
        )
        TimetableParseResult.NoDayHeader -> TimetableScan(emptyList(), "요일(월 화 수 …)을 찾지 못했어요. 시간표 전체가 보이게 찍은 사진인지 확인해 주세요.")
        TimetableParseResult.NoHourLabels -> TimetableScan(emptyList(), "왼쪽 시각(9, 10, 11 …)을 찾지 못했어요. 시간표 전체가 보이게 찍은 사진인지 확인해 주세요.")
    }
}
