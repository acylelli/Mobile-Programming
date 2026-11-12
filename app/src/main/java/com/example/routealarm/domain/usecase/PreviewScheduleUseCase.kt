package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.InvalidInputReason
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.domain.policy.TravelEstimatePolicy
import com.example.routealarm.domain.repository.LocationRepository
import com.example.routealarm.domain.repository.TransitRepository
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** 일정 생성 화면에서 입력 중인 값 */
data class ScheduleDraft(
    val title: String,
    val origin: Place?,
    val destination: Place?,
    val useCurrentLocation: Boolean = false,
    val targetArrivalTime: LocalTime,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    /** 1회 일정의 날짜. null 이면 가장 가까운 날(오늘 또는 내일)로 정한다. 반복 일정에서는 무시 */
    val oneTimeDate: LocalDate? = null,
    val preparationMinutes: Int,
    val bufferMinutes: Int,
    val alarmMode: AlarmMode = AlarmMode.WAKE_UP_AND_DEPARTURE,
)

/** 저장 전 "이렇게 계산했어요" 결과 화면에 보여줄 값 */
data class SchedulePreview(
    val draft: ScheduleDraft,
    val resolvedOrigin: Place,
    val destination: Place,
    val targetArrival: Instant,
    val oneTimeDate: LocalDate?,
    val route: TransitRoute,
    val outcome: PlanOutcome,
)

/**
 * 입력값으로 경로를 조회하고 알람 계획을 미리 계산한다. 아직 저장/알람 등록은 하지 않는다.
 */
class PreviewScheduleUseCase @Inject constructor(
    private val transitRepository: TransitRepository,
    private val locationRepository: LocationRepository,
    private val resolveNextOccurrence: ResolveNextOccurrenceUseCase,
    private val buildAlarmPlan: BuildAlarmPlanUseCase,
    private val timeProvider: TimeProvider,
) {

    suspend operator fun invoke(draft: ScheduleDraft): AppResult<SchedulePreview> {
        val destination = draft.destination ?: return invalid(InvalidInputReason.MISSING_DESTINATION)
        val origin = when (val result = resolveOrigin(draft)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        if (origin.isSameLocationAs(destination)) return invalid(InvalidInputReason.SAME_ORIGIN_AND_DESTINATION)
        if (draft.preparationMinutes < 0 || draft.bufferMinutes < 0) {
            return invalid(InvalidInputReason.NEGATIVE_DURATION)
        }

        val now = timeProvider.now()
        val zone = timeProvider.zone()
        val oneTimeDate = if (draft.repeatDays.isEmpty()) {
            // 사용자가 고른 날짜가 이미 지났다면 아래 BuildAlarmPlan 이 TARGET_IN_PAST 로 알려준다.
            draft.oneTimeDate ?: resolveNextOccurrence.resolveOneTimeDate(draft.targetArrivalTime, now, zone)
        } else {
            null
        }
        val fixedLead = Duration.ofMinutes((draft.preparationMinutes + draft.bufferMinutes).toLong())
        val guessTarget = resolveNextOccurrence(
            draft.targetArrivalTime, draft.repeatDays, oneTimeDate, now, zone,
            leadTime = fixedLead.plusMinutes(TravelEstimatePolicy.FALLBACK_TRAVEL_MINUTES),
        ) ?: return invalid(InvalidInputReason.NO_REPEAT_DAY)

        val departureGuess = guessTarget.minus(fixedLead.plusMinutes(TravelEstimatePolicy.FALLBACK_TRAVEL_MINUTES))
        val query = when (val result = transitRepository.getRoutes(origin, destination, departureGuess)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val route = query.bestRoute ?: return AppResult.Failure(AppError.NoRoute)

        // 반복 일정은 실제 이동시간을 알게 된 뒤 "아직 준비가 가능한" 회차로 다시 고른다.
        val target = if (draft.repeatDays.isEmpty()) {
            guessTarget
        } else {
            resolveNextOccurrence(
                draft.targetArrivalTime, draft.repeatDays, null, now, zone,
                leadTime = fixedLead.plus(route.totalDuration).plusMinutes(LEAD_MARGIN_MINUTES),
            ) ?: guessTarget
        }

        val input = PlanInput(
            targetArrival = target,
            currentTravel = route.totalDuration,
            delay = route.delay,
            preparation = Duration.ofMinutes(draft.preparationMinutes.toLong()),
            baseBufferMinutes = draft.bufferMinutes,
            isRealtime = query.isRealtime,
            fetchedAt = query.fetchedAt,
            route = route,
        )
        return when (val outcome = buildAlarmPlan(input, now)) {
            is AppResult.Success -> AppResult.Success(
                SchedulePreview(draft, origin, destination, target, oneTimeDate, route, outcome.data),
            )
            is AppResult.Failure -> outcome
        }
    }

    private suspend fun resolveOrigin(draft: ScheduleDraft): AppResult<Place> {
        if (!draft.useCurrentLocation) {
            return draft.origin?.let { AppResult.Success(it) } ?: invalid(InvalidInputReason.MISSING_ORIGIN)
        }
        val placeholder = draft.origin ?: return invalid(InvalidInputReason.MISSING_ORIGIN)
        return when (val location = locationRepository.getCurrentLocation()) {
            is AppResult.Success -> AppResult.Success(
                placeholder.copy(latitude = location.data.latitude, longitude = location.data.longitude),
            )
            is AppResult.Failure -> location
        }
    }

    private fun invalid(reason: InvalidInputReason) = AppResult.Failure(AppError.InvalidInput(reason))

    private companion object {
        /** 지연 추가 여유 등 계산 과정에서 늘어날 수 있는 시간 */
        const val LEAD_MARGIN_MINUTES = 15L
    }
}

/**
 * "만약 15분 늦어진다면?"(UI-C-006 ⑤) — 지연이 생겼을 때 기상 시각이 어떻게 바뀌는지 미리 계산한다.
 *
 * 실제 재계산과 같은 [BuildAlarmPlanUseCase] 를 그대로 쓴다. 미리보기에서 보여준 시각과
 * 나중에 실제로 조정되는 시각이 달라지면 안 되기 때문이다(지연이 생기면 지연 보정 여유도 함께 늘어난다).
 */
class PreviewDelayScenarioUseCase @Inject constructor(
    private val buildAlarmPlan: BuildAlarmPlanUseCase,
) {
    /** @return 지연을 반영해 다시 계산한 기상 시각. 계산할 수 없으면 null */
    operator fun invoke(preview: SchedulePreview, delayMinutes: Int = DEFAULT_DELAY_MINUTES): Instant? {
        val plan = preview.outcome.plan
        val input = PlanInput(
            targetArrival = plan.targetArrival,
            currentTravel = Duration.ofMinutes((plan.travelMinutes + delayMinutes).toLong()),
            delay = Duration.ofMinutes((plan.delayMinutes + delayMinutes).toLong()),
            preparation = Duration.ofMinutes(preview.draft.preparationMinutes.toLong()),
            baseBufferMinutes = preview.draft.bufferMinutes,
            isRealtime = plan.isRealtime,
            fetchedAt = plan.lastUpdatedAt,
        )
        // "지금"에 따라 결과가 달라지지 않도록(지각 처리 등) 충분히 과거 시점을 기준으로 순수하게 계산한다.
        val outcome = buildAlarmPlan(input, now = Instant.EPOCH)
        return (outcome as? AppResult.Success)?.data?.plan?.wakeUp
    }

    companion object {
        const val DEFAULT_DELAY_MINUTES = 15
    }
}
