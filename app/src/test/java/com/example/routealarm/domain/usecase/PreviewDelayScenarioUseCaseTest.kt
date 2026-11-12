package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * 계산 미리보기의 "만약 15분 늦어진다면?" 카드가 실제 재계산과 같은 결과를 보여주는지 검증한다.
 */
class PreviewDelayScenarioUseCaseTest {

    private val day = LocalDate.of(2026, 10, 23)
    private val now = seoul(day.minusDays(1), 22, 0)
    private val buildPlan = TestData.buildAlarmPlanUseCase()
    private val scenario = PreviewDelayScenarioUseCase(buildPlan)

    private fun preview(travelMinutes: Long): SchedulePreview {
        val draft = ScheduleDraft(
            title = "",
            origin = TestData.home,
            destination = TestData.school,
            targetArrivalTime = LocalTime.of(9, 0),
            preparationMinutes = 30,
            bufferMinutes = 10,
        )
        val input = PlanInput(
            targetArrival = seoul(day, 9, 0),
            currentTravel = Duration.ofMinutes(travelMinutes),
            preparation = Duration.ofMinutes(30),
            baseBufferMinutes = 10,
            isRealtime = true,
            fetchedAt = now,
        )
        val outcome = (buildPlan(input, now) as AppResult.Success).data
        val route = com.example.routealarm.domain.model.TransitRoute(
            totalDuration = Duration.ofMinutes(travelMinutes),
            walkingDuration = Duration.ZERO,
            waitingDuration = Duration.ZERO,
            transferDuration = Duration.ZERO,
            departureTime = outcome.plan.departure,
            arrivalTime = outcome.plan.estimatedArrival,
            transferCount = 0,
            segments = emptyList(),
        )
        return SchedulePreview(draft, TestData.home, TestData.school, seoul(day, 9, 0), day, route, outcome)
    }

    @Test
    fun `65분 예시에 15분 지연이 생기면 이동 15분과 지연 여유 5분만큼 알람이 당겨진다`() {
        val preview = preview(travelMinutes = 65)
        assertEquals(seoul(day, 7, 15), preview.outcome.plan.wakeUp)

        // 이동 80분 + 여유 15분(기본 10 + 지연 보정 5) + 준비 30분
        assertEquals(seoul(day, 6, 55), scenario(preview))
    }

    @Test
    fun `지연 시나리오는 실제 재계산과 같은 시각을 보여준다`() {
        val preview = preview(travelMinutes = 65)
        val recalculated = buildPlan(
            PlanInput(
                targetArrival = seoul(day, 9, 0),
                currentTravel = Duration.ofMinutes(80),
                delay = Duration.ofMinutes(15),
                preparation = Duration.ofMinutes(30),
                baseBufferMinutes = 10,
                isRealtime = true,
                fetchedAt = now,
            ),
            now,
        )

        assertEquals((recalculated as AppResult.Success).data.plan.wakeUp, scenario(preview))
    }

    @Test
    fun `지연이 없으면 기상 시각은 그대로다`() {
        val preview = preview(travelMinutes = 65)

        assertEquals(preview.outcome.plan.wakeUp, scenario(preview, delayMinutes = 0))
    }
}
