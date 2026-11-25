package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.TimetableAlarmProposal
import com.example.routealarm.domain.policy.TimetablePolicy
import com.example.routealarm.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import javax.inject.Inject

/** 시간표로 알람을 만든 결과 */
data class TimetableAlarmResult(
    val created: List<Long>,
    /** 만들지 못한 제안과 이유(경로 없음, 네트워크 오류 등) */
    val failed: List<Pair<TimetableAlarmProposal, AppError>>,
    /** 새 알람으로 바뀌어 지운 예전 시간표 알람 수 */
    val replaced: Int = 0,
)

/**
 * 시간표의 알람 제안(요일별 첫 수업)마다 반복 일정을 만든다.
 * 일정 하나를 직접 만들 때와 똑같이 경로를 조회해 미리 계산(Preview)한 뒤 저장(Save)하므로,
 * 알람 계산·재확인·조정 규칙이 그대로 적용된다.
 * 예전에 시간표로 만든 알람이 있으면(학기 변경 등) 새 알람이 모두 만들어진 뒤 지워 알람이 겹치지 않게 한다.
 */
class CreateSchedulesFromTimetableUseCase @Inject constructor(
    private val previewSchedule: PreviewScheduleUseCase,
    private val saveSchedule: SaveScheduleUseCase,
    private val deleteSchedule: DeleteScheduleUseCase,
    private val timetableRepository: TimetableRepository,
) {
    suspend operator fun invoke(
        proposals: List<TimetableAlarmProposal>,
        origin: Place,
        destination: Place,
        preparationMinutes: Int,
        bufferMinutes: Int,
    ): TimetableAlarmResult {
        val created = mutableListOf<Long>()
        val failed = mutableListOf<Pair<TimetableAlarmProposal, AppError>>()
        for (proposal in proposals) {
            val draft = ScheduleDraft(
                title = titleOf(proposal),
                origin = origin,
                destination = destination,
                targetArrivalTime = proposal.arrival,
                repeatDays = proposal.days,
                preparationMinutes = preparationMinutes,
                bufferMinutes = bufferMinutes,
            )
            val preview = when (val result = previewSchedule(draft)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    failed += proposal to result.error
                    continue
                }
            }
            when (val saved = saveSchedule(preview)) {
                is AppResult.Success -> created += saved.data.scheduleId
                is AppResult.Failure -> failed += proposal to saved.error
            }
        }
        val previous = timetableRepository.alarmScheduleIds.first()
        val replacement = TimetablePolicy.alarmReplacement(previous, created, anyFailed = failed.isNotEmpty())
        // 사용자가 이미 지운 알람은 없어도 괜찮다(삭제 실패는 무시).
        replacement.delete.forEach { deleteSchedule(it) }
        timetableRepository.setAlarmScheduleIds(replacement.remember)
        return TimetableAlarmResult(created, failed, replaced = replacement.delete.size)
    }

    companion object {
        private val DAY_LABELS = mapOf(
            DayOfWeek.MONDAY to "월", DayOfWeek.TUESDAY to "화", DayOfWeek.WEDNESDAY to "수", DayOfWeek.THURSDAY to "목",
            DayOfWeek.FRIDAY to "금", DayOfWeek.SATURDAY to "토", DayOfWeek.SUNDAY to "일",
        )

        /** "수·목 첫 수업" — 알람 목록에서 어떤 요일 알람인지 바로 알 수 있게 한다. */
        fun titleOf(proposal: TimetableAlarmProposal): String =
            proposal.days.sorted().joinToString("·") { DAY_LABELS.getValue(it) } + " 첫 수업"
    }
}
