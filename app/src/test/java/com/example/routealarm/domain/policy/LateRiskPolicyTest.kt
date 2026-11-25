package com.example.routealarm.domain.policy

import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.fake.TestData.seoul
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LateRiskPolicyTest {

    private val day = LocalDate.of(2026, 10, 6)
    private val zone = ZoneId.of("Asia/Seoul")
    private val nine = LocalTime.of(9, 0)

    /** 09:00 도착 목표, 이동 60분, 준비 30분: 07:20 기상, 07:50 출발 → 08:50 도착 예정 */
    private val plan = AlarmPlan(
        targetArrival = seoul(day, 9, 0),
        estimatedArrival = seoul(day, 8, 50),
        departure = seoul(day, 7, 50),
        wakeUp = seoul(day, 7, 20),
        travelMinutes = 60,
        safetyBufferMinutes = 10,
        delayMinutes = 0,
        isRealtime = true,
        lastUpdatedAt = seoul(day, 7, 0),
    )

    /** 출발 알림 뒤 다음 회차(내일)로 넘어간 계획 */
    private val tomorrowPlan = plan.copy(
        targetArrival = seoul(day.plusDays(1), 9, 0),
        estimatedArrival = seoul(day.plusDays(1), 8, 50),
        departure = seoul(day.plusDays(1), 7, 50),
        wakeUp = seoul(day.plusDays(1), 7, 20),
    )

    private fun dismissedAt(hour: Int, minute: Int) =
        AlarmHistoryEvent(scheduleId = 1, type = AlarmEventType.DISMISSED, occurredAt = seoul(day, hour, minute), newWakeUp = seoul(day, 7, 20))

    private fun assess(plan: AlarmPlan, dismissal: AlarmHistoryEvent?, now: Instant) =
        LateRiskPolicy.assess(plan, nine, preparationMinutes = 30, lastDismissal = dismissal, now = now, zone = zone)

    @Test
    fun `제때 일어나 계획대로 가면 지각 위험이 아니다`() {
        assertNull(assess(plan, dismissedAt(7, 22), seoul(day, 7, 40)))
        // 제때 나가서 이동 중(출발 알림 뒤 계획은 내일로 넘어감)이어도 경고하지 않는다
        assertNull(assess(tomorrowPlan, dismissedAt(7, 22), seoul(day, 8, 30)))
    }

    @Test
    fun `알람을 늦게 끄면 끈 시각과 준비 시간으로 늦는 시간을 계산한다`() {
        // 07:50 에 끔 → 08:20 출발 → 이동 60분 → 09:20 도착, 20분 지각
        val risk = assess(tomorrowPlan, dismissedAt(7, 50), seoul(day, 7, 55))!!

        assertEquals(LateRisk.Reason.OVERSLEPT, risk.reason)
        assertEquals(seoul(day, 9, 0), risk.target)
        assertEquals(seoul(day, 8, 20), risk.leaveAt)
        assertEquals(seoul(day, 9, 20), risk.transitArrival)
        assertEquals(20, risk.lateMinutes)
    }

    @Test
    fun `출발 전인데 교통이 나빠져 계획대로 가도 늦으면 알린다`() {
        val worse = plan.copy(estimatedArrival = seoul(day, 9, 15))

        val risk = assess(worse, null, seoul(day, 7, 30))!!

        assertEquals(LateRisk.Reason.TRAFFIC, risk.reason)
        assertEquals(15, risk.lateMinutes)
        // 출발 시각이 지나면(이동 중일 수 있으므로) 교통 악화 경고는 내린다
        assertNull(assess(worse, null, seoul(day, 7, 55)))
    }

    @Test
    fun `목표 3시간 전보다 이르거나 목표 시각이 지나면 판단하지 않는다`() {
        assertNull(assess(tomorrowPlan, dismissedAt(7, 50), seoul(day, 5, 59)))
        assertNull(assess(tomorrowPlan, dismissedAt(7, 50), seoul(day, 9, 0)))
    }

    @Test
    fun `택시 도착 예상은 출발하게 될 시각(지났으면 지금)부터 택시 소요 시간 뒤`() {
        val risk = assess(tomorrowPlan, dismissedAt(7, 50), seoul(day, 7, 55))!!

        assertEquals(seoul(day, 8, 44), LateRiskPolicy.taxiArrival(risk, seoul(day, 7, 55), 24))
        assertEquals(seoul(day, 8, 54), LateRiskPolicy.taxiArrival(risk, seoul(day, 8, 30), 24))
    }
}
