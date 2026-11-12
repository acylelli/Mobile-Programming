package com.example.routealarm.domain.usecase

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.PlanLimits
import com.example.routealarm.domain.model.PlanType
import com.example.routealarm.domain.model.ScheduleQuota
import com.example.routealarm.domain.model.SubscriptionProduct
import com.example.routealarm.fake.FakeAlarmScheduler
import com.example.routealarm.fake.FakeRefreshScheduler
import com.example.routealarm.fake.FakeScheduleRepository
import com.example.routealarm.fake.FakeSubscriptionRepository
import com.example.routealarm.fake.FakeTimeProvider
import com.example.routealarm.fake.TestData
import com.example.routealarm.fake.TestData.seoul
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

/**
 * 무료/PRO/단체 이용권 규칙(SFR-015, SFR-016)을 결제·서버 없이 검증한다.
 */
class SubscriptionUseCasesTest {

    private val day = LocalDate.of(2026, 10, 23)
    private val time = FakeTimeProvider(seoul(day.minusDays(1), 22, 0))
    private val schedules = FakeScheduleRepository()
    private val subscription = FakeSubscriptionRepository(time = time)
    private val alarmScheduler = FakeAlarmScheduler()
    private val refreshScheduler = FakeRefreshScheduler()

    private val resolve = ResolveNextOccurrenceUseCase()
    private val scheduleAlarm = ScheduleAlarmUseCase(alarmScheduler, refreshScheduler, subscription, time)
    private val sync = SyncScheduleUseCase(schedules, resolve, TestData.buildAlarmPlanUseCase(), scheduleAlarm, time)
    private val restore = RestoreAlarmsUseCase(schedules, sync)
    private val quota = CheckScheduleQuotaUseCase(schedules, subscription, time)

    private suspend fun addSchedule(isDemo: Boolean = false, enabled: Boolean = true): Long {
        val result = schedules.upsert(TestData.schedule(oneTimeDate = day, enabled = enabled).copy(isDemo = isDemo))
        return (result as AppResult.Success).data
    }

    @Test
    fun `연간 요금은 월 2000원꼴이고 월간 대비 31퍼센트 할인이다`() {
        assertEquals(24_000, SubscriptionProduct.YEARLY.priceWon)
        assertEquals(2_900, SubscriptionProduct.MONTHLY.priceWon)
        assertEquals(2_000, PlanLimits.YEARLY_MONTHLY_EQUIVALENT_WON)
        assertEquals(31, PlanLimits.YEARLY_DISCOUNT_PERCENT)
    }

    @Test
    fun `무료 플랜은 일정 2개까지 만들 수 있다`() = runTest {
        assertEquals(ScheduleQuota.Limited(used = 0, limit = 2), quota())
        addSchedule()
        assertFalse((quota() as ScheduleQuota.Limited).reached)
        addSchedule()
        assertTrue((quota() as ScheduleQuota.Limited).reached)
    }

    @Test
    fun `예시 일정은 무료 플랜 한도에 세지 않는다`() = runTest {
        addSchedule(isDemo = true, enabled = false)
        addSchedule()

        assertEquals(ScheduleQuota.Limited(used = 1, limit = 2), quota())
    }

    @Test
    fun `PRO 는 일정 수 제한이 없다`() = runTest {
        repeat(3) { addSchedule() }
        subscription.startTrial(SubscriptionProduct.YEARLY)

        assertEquals(ScheduleQuota.Unlimited, quota())
    }

    @Test
    fun `무료체험을 시작하면 7일 뒤 첫 결제가 예정된 PRO 가 된다`() = runTest {
        val result = StartProTrialUseCase(subscription, restore)(SubscriptionProduct.YEARLY)

        val entitlement = (result as AppResult.Success).data
        assertEquals(PlanType.PRO_YEARLY, entitlement.plan)
        assertTrue(entitlement.isPro(time.instant))
        assertTrue(entitlement.isInTrial(time.instant))
        assertEquals(time.instant.plus(Duration.ofDays(7)), entitlement.trialEndsAt)
        assertFalse(entitlement.isInTrial(time.instant.plus(Duration.ofDays(8))))
    }

    @Test
    fun `PRO 로 바뀌면 교통 재확인이 실시간 기준으로 다시 예약된다`() = runTest {
        val id = addSchedule()
        val plan = TestData.buildAlarmPlanUseCase()(
            PlanInput(
                targetArrival = seoul(day, 9, 0),
                currentTravel = Duration.ofMinutes(60),
                preparation = Duration.ofMinutes(30),
                baseBufferMinutes = 10,
                isRealtime = true,
                fetchedAt = time.instant,
            ),
            time.instant,
        ).let { (it as AppResult.Success).data.plan }
        schedules.upsert(schedules.getSchedule(id)!!.copy(plan = plan))
        restore()
        // 무료: 기상(07:20) 30분 전 한 번
        assertEquals(seoul(day, 6, 50), refreshScheduler.scheduled[id])

        StartProTrialUseCase(subscription, restore)(SubscriptionProduct.MONTHLY)

        // PRO: 가장 가까운 체크포인트(3시간 전)부터 출발 직전까지
        assertEquals(seoul(day, 4, 20), refreshScheduler.scheduled[id])
    }

    @Test
    fun `해지한 구독은 결제 기간이 끝나면 무료가 되고 일정은 남는다`() = runTest {
        repeat(3) { addSchedule() }
        val expiresAt = time.instant.plus(Duration.ofDays(10))
        val cancelled = Entitlement(plan = PlanType.PRO_MONTHLY, expiresAt = expiresAt, autoRenew = false)

        assertTrue(cancelled.isPro(time.instant))
        assertFalse(cancelled.isPro(expiresAt.plusSeconds(1)))
        assertEquals(
            ScheduleQuota.Limited(used = 3, limit = 2),
            quota.evaluate(schedules.all, cancelled, expiresAt.plusSeconds(1)),
        )
        assertEquals(3, schedules.all.size)
    }

    @Test
    fun `코드는 대문자로 바꾸고 허용되지 않는 문자는 버린다`() {
        assertEquals("HSU-2026-WAKE7", GroupPassCode.normalize(" hsu-2026-wake7 "))
        assertEquals("HSU2026", GroupPassCode.normalize("hsu_2026!"))
        // 입력창에는 친 그대로 남기고(대소문자 유지) 허용되지 않는 문자만 거른다.
        assertEquals("hsu-2026", GroupPassCode.sanitize("hsu-2026 한글"))
        assertEquals(GroupPassCode.MAX_LENGTH, GroupPassCode.sanitize("A".repeat(40)).length)
        assertTrue(GroupPassCode.isComplete("HSU-2026-WAKE7"))
        assertFalse(GroupPassCode.isComplete("HSU-2026-"))
        assertFalse(GroupPassCode.isComplete("HSU-20X6-WAKE7"))
    }

    @Test
    fun `형식이 틀린 코드는 서버에 묻지 않고 거절한다`() = runTest {
        subscription.failWith = AppError.Network // 서버에 갔다면 Network 가 나온다.

        val result = VerifyGroupPassUseCase(subscription)("hsu-2026")

        assertEquals(AppResult.Failure(AppError.GroupPassInvalid), result)
    }

    @Test
    fun `단체 이용권을 확인하면 기관과 이용 기간을 돌려준다`() = runTest {
        subscription.passes["HSU-2026-WAKE7"] = FakeSubscriptionRepository.hansungPass()

        val result = VerifyGroupPassUseCase(subscription)("hsu-2026-wake7")

        val pass = (result as AppResult.Success).data
        assertEquals("한성대학교", pass.organization)
        assertEquals(LocalDate.of(2027, 2, 28), pass.validUntil)
        // 확인만으로는 플랜이 바뀌지 않는다.
        assertEquals(PlanType.FREE, subscription.current().plan)
    }

    @Test
    fun `단체 이용권을 적용하면 이용 기간 마지막 날까지 PRO 다`() = runTest {
        subscription.passes["HSU-2026-WAKE7"] = FakeSubscriptionRepository.hansungPass()

        val result = ApplyGroupPassUseCase(subscription, restore, time)("HSU-2026-WAKE7")

        val applied = (result as AppResult.Success).data
        assertFalse(applied.hadPersonalSubscription)
        assertEquals(PlanType.GROUP_PASS, applied.entitlement.plan)
        assertEquals("한성대학교", applied.entitlement.organization)
        assertTrue(applied.entitlement.isPro(seoul(LocalDate.of(2027, 2, 28), 23, 59)))
        assertFalse(applied.entitlement.isPro(seoul(LocalDate.of(2027, 3, 1), 0, 1)))
    }

    @Test
    fun `개인 구독 중에 단체 이용권을 적용하면 자동 갱신 해지 안내가 필요하다`() = runTest {
        subscription.passes["HSU-2026-WAKE7"] = FakeSubscriptionRepository.hansungPass()
        subscription.startTrial(SubscriptionProduct.MONTHLY)

        val result = ApplyGroupPassUseCase(subscription, restore, time)("HSU-2026-WAKE7")

        assertTrue((result as AppResult.Success).data.hadPersonalSubscription)
    }

    @Test
    fun `검증 서버에 연결하지 못하면 마지막으로 확인된 플랜을 유지한다`() = runTest {
        subscription.startTrial(SubscriptionProduct.YEARLY)
        subscription.passes["HSU-2026-WAKE7"] = FakeSubscriptionRepository.hansungPass()
        subscription.failWith = AppError.Network

        val result = ApplyGroupPassUseCase(subscription, restore, time)("HSU-2026-WAKE7")

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(PlanType.PRO_YEARLY, subscription.current().plan)
    }

    @Test
    fun `무료 플랜으로 되돌려도 일정은 삭제하지 않는다`() = runTest {
        subscription.startTrial(SubscriptionProduct.YEARLY)
        repeat(3) { addSchedule() }

        ResetSubscriptionUseCase(subscription, restore)()

        assertEquals(PlanType.FREE, subscription.current().plan)
        assertEquals(3, schedules.all.size)
    }
}
