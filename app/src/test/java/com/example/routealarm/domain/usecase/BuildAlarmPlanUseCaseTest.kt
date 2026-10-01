package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.InvalidInputReason
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class BuildAlarmPlanUseCaseTest {

    private val buildPlan = TestData.buildAlarmPlanUseCase()
    private val day = LocalDate.of(2026, 10, 6)
    private val eveningBefore = seoul(day.minusDays(1), 22, 0)

    private fun input(
        target: java.time.Instant = seoul(day, 9, 0),
        travel: Long = 60,
        buffer: Int = 10,
        preparation: Long = 30,
        delay: Long = 0,
        isRealtime: Boolean = true,
    ) = PlanInput(
        targetArrival = target,
        currentTravel = Duration.ofMinutes(travel),
        delay = Duration.ofMinutes(delay),
        preparation = Duration.ofMinutes(preparation),
        baseBufferMinutes = buffer,
        isRealtime = isRealtime,
        fetchedAt = eveningBefore,
    )

    private fun onTime(result: AppResult<PlanOutcome>): PlanOutcome.OnTime {
        assertTrue("expected success but was $result", result is AppResult.Success)
        val outcome = (result as AppResult.Success).data
        assertTrue("expected OnTime but was $outcome", outcome is PlanOutcome.OnTime)
        return outcome as PlanOutcome.OnTime
    }

    @Test
    fun `9시 도착 60분 이동 10분 여유 30분 준비면 7시 20분 알람`() {
        val plan = onTime(buildPlan(input(), now = eveningBefore)).plan

        assertEquals(seoul(day, 7, 50), plan.departure)
        assertEquals(seoul(day, 7, 20), plan.wakeUp)
        assertEquals(seoul(day, 8, 50), plan.estimatedArrival)
        assertEquals(10L, plan.slackMinutes)
    }

    @Test
    fun `명세 예시 - 65분 이동이면 7시 45분 출발 7시 15분 알람`() {
        val plan = onTime(buildPlan(input(travel = 65), now = eveningBefore)).plan

        assertEquals(seoul(day, 7, 45), plan.departure)
        assertEquals(seoul(day, 7, 15), plan.wakeUp)
    }

    @Test
    fun `교통 지연으로 이동시간이 15분 늘면 지연 여유까지 더해 알람이 당겨진다`() {
        val plan = onTime(buildPlan(input(travel = 80, delay = 15), now = eveningBefore)).plan

        // buffer = 10 + ceil(15 * 0.3) = 15
        assertEquals(15, plan.safetyBufferMinutes)
        assertEquals(seoul(day, 7, 25), plan.departure)
        assertEquals(seoul(day, 6, 55), plan.wakeUp)
    }

    @Test
    fun `자정을 넘어가는 일정은 전날 밤으로 계산된다`() {
        val target = seoul(day, 0, 30)
        val plan = onTime(buildPlan(input(target = target), now = seoul(day.minusDays(1), 18, 0))).plan

        assertEquals(seoul(day.minusDays(1), 23, 20), plan.departure)
        assertEquals(seoul(day.minusDays(1), 22, 50), plan.wakeUp)
    }

    @Test
    fun `다음날 일정도 정확히 계산된다`() {
        val nextDay = day.plusDays(1)
        val plan = onTime(buildPlan(input(target = seoul(nextDay, 9, 0)), now = eveningBefore)).plan

        assertEquals(seoul(nextDay, 7, 20), plan.wakeUp)
    }

    @Test
    fun `DST 시작일에는 벽시계가 아니라 실제 경과시간 기준으로 계산한다`() {
        val newYork = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2026, 3, 8) // 02:00 EST -> 03:00 EDT
        val target = LocalDateTime.of(dstDay, LocalTime.of(3, 30)).atZone(newYork).toInstant()
        val now = LocalDateTime.of(dstDay.minusDays(1), LocalTime.of(22, 0)).atZone(newYork).toInstant()

        val plan = onTime(buildPlan(input(target = target), now = now)).plan

        // 실제로 100분 전은 EST 00:50 (벽시계로 빼면 01:50 이 되어 1시간 늦게 깨운다)
        assertEquals(LocalTime.of(0, 50), plan.wakeUp.atZone(newYork).toLocalTime())
        assertEquals(LocalTime.of(1, 20), plan.departure.atZone(newYork).toLocalTime())
        assertEquals(Duration.ofMinutes(100), Duration.between(plan.wakeUp, target))
    }

    @Test
    fun `이동시간이 0이면 여유시간과 준비시간만 뺀다`() {
        val plan = onTime(buildPlan(input(travel = 0), now = eveningBefore)).plan

        assertEquals(seoul(day, 8, 50), plan.departure)
        assertEquals(seoul(day, 8, 20), plan.wakeUp)
        assertEquals(0, plan.travelMinutes)
    }

    @Test
    fun `음수 입력은 잘못된 입력 오류`() {
        val result = buildPlan(input(preparation = -5), now = eveningBefore)

        assertEquals(
            AppResult.Failure(AppError.InvalidInput(InvalidInputReason.NEGATIVE_DURATION)),
            result,
        )
    }

    @Test
    fun `현재보다 과거 목표시간은 오류`() {
        val result = buildPlan(input(), now = seoul(day, 9, 30))

        assertEquals(
            AppResult.Failure(AppError.InvalidInput(InvalidInputReason.TARGET_IN_PAST)),
            result,
        )
    }

    @Test
    fun `지금 출발해도 늦으면 TooLate 와 예상 도착시간을 알려준다`() {
        // 현재 08:30, 목표 09:00, 이동 60분
        val now = seoul(day, 8, 30)
        val outcome = (buildPlan(input(), now = now) as AppResult.Success).data

        assertTrue(outcome is PlanOutcome.TooLate)
        outcome as PlanOutcome.TooLate
        assertEquals(seoul(day, 9, 30), outcome.earliestArrival)
        assertEquals(30L, outcome.lateByMinutes)
        assertEquals(now, outcome.plan.departure)
    }

    @Test
    fun `기상 시각은 지났지만 지금 출발하면 늦지 않는 경우`() {
        val now = seoul(day, 7, 30) // 기상 07:20 은 지났고 출발 07:50 은 남음
        val outcome = onTime(buildPlan(input(), now = now))

        assertTrue(outcome.wakeUpAlreadyPassed)
        assertEquals(seoul(day, 7, 50), outcome.plan.departure)
    }

    @Test
    fun `실시간이 아닌 캐시 데이터면 불확실성 여유를 더한다`() {
        val plan = onTime(buildPlan(input(isRealtime = false), now = eveningBefore)).plan

        assertFalse(plan.isRealtime)
        assertEquals(15, plan.safetyBufferMinutes)
        assertEquals(seoul(day, 7, 15), plan.wakeUp)
    }
}
