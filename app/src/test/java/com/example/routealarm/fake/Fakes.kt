package com.example.routealarm.fake

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.model.ColorTheme
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.GroupPass
import com.example.routealarm.domain.model.GeoPoint
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.MetricsSummary
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlanType
import com.example.routealarm.domain.model.RouteQueryResult
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.SubscriptionProduct
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.domain.model.TransitSegment
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.AlarmNotifier
import com.example.routealarm.domain.repository.AlarmScheduler
import com.example.routealarm.domain.repository.LocationRepository
import com.example.routealarm.domain.repository.MetricsRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.SubscriptionRepository
import com.example.routealarm.domain.repository.TransitRefreshScheduler
import com.example.routealarm.domain.repository.TransitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FakeTimeProvider(
    var instant: Instant,
    var zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}

/** API 없이 이동시간/지연/실패를 조절할 수 있는 교통 저장소 */
class FakeTransitRepository(
    var travelMinutes: Long = 60,
    var delayMinutes: Long = 0,
    var failWith: AppError? = null,
    var isRealtime: Boolean = true,
    var fetchedAt: Instant = Instant.EPOCH,
    /** 서버가 붙여 주는 첫 탑승 실시간 도착 안내("약 8분 후 도착") */
    var arrivalInfo: String? = null,
) : TransitRepository {
    var callCount = 0
        private set
    var lastDepartureTime: Instant? = null
        private set

    override suspend fun getRoutes(origin: Place, destination: Place, departureTime: Instant): AppResult<RouteQueryResult> {
        callCount++
        lastDepartureTime = departureTime
        failWith?.let { return AppResult.Failure(it) }
        val total = Duration.ofMinutes(travelMinutes)
        val route = TransitRoute(
            totalDuration = total,
            walkingDuration = Duration.ofMinutes(10),
            waitingDuration = Duration.ofMinutes(3),
            transferDuration = Duration.ZERO,
            departureTime = departureTime,
            arrivalTime = departureTime.plus(total),
            transferCount = 0,
            segments = listOf(
                TransitSegment(SegmentType.SUBWAY, "8호선", origin.name, destination.name, total, arrivalInfo),
            ),
            delay = Duration.ofMinutes(delayMinutes),
        )
        return AppResult.Success(RouteQueryResult(listOf(route), isRealtime, fetchedAt))
    }
}

class FakeScheduleRepository : ScheduleRepository {
    private val schedules = MutableStateFlow<Map<Long, Schedule>>(emptyMap())
    private var nextId = 1L

    val all: List<Schedule> get() = schedules.value.values.toList()

    override fun observeSchedules(): Flow<List<Schedule>> = schedules.map { it.values.toList() }
    override fun observeSchedule(id: Long): Flow<Schedule?> = schedules.map { it[id] }
    override suspend fun getSchedule(id: Long): Schedule? = schedules.value[id]
    override suspend fun getEnabledSchedules(): List<Schedule> = all.filter { it.enabled }

    override suspend fun upsert(schedule: Schedule): AppResult<Long> {
        val id = if (schedule.id == 0L) nextId++ else schedule.id
        schedules.update { it + (id to schedule.copy(id = id)) }
        return AppResult.Success(id)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        schedules.update { it - id }
        return AppResult.Success(Unit)
    }

}

class FakeAlarmScheduler(var exactAllowed: Boolean = true) : AlarmScheduler {
    val scheduled = mutableMapOf<Pair<Long, AlarmType>, Instant>()

    override fun canScheduleExactAlarms(): Boolean = exactAllowed

    override fun schedule(scheduleId: Long, type: AlarmType, triggerAt: Instant): Boolean {
        scheduled[scheduleId to type] = triggerAt
        return exactAllowed
    }

    override fun cancel(scheduleId: Long, types: Collection<AlarmType>) {
        types.forEach { scheduled.remove(scheduleId to it) }
    }
}

class FakeRefreshScheduler : TransitRefreshScheduler {
    val scheduled = mutableMapOf<Long, Instant>()
    override fun scheduleRefresh(scheduleId: Long, runAt: Instant) {
        scheduled[scheduleId] = runAt
    }

    override fun cancelRefresh(scheduleId: Long) {
        scheduled.remove(scheduleId)
    }
}

class FakeAlarmNotifier : AlarmNotifier {
    val adjusted = mutableListOf<Pair<Instant, Instant>>()
    val suggested = mutableListOf<Pair<Long, Boolean>>()

    override fun notifyAlarmAdjusted(schedule: Schedule, previousWakeUp: Instant, newWakeUp: Instant, isRealtime: Boolean) {
        adjusted += previousWakeUp to newWakeUp
    }

    override fun notifyAdjustmentSuggested(schedule: Schedule, deltaMinutes: Long, needsConfirmation: Boolean) {
        suggested += deltaMinutes to needsConfirmation
    }
}

class FakeAlarmHistoryRepository : AlarmHistoryRepository {
    val events = mutableListOf<AlarmHistoryEvent>()
    var recentTravel: List<Duration> = emptyList()

    override suspend fun record(event: AlarmHistoryEvent) {
        events += event
    }

    override fun observeHistory(scheduleId: Long, limit: Int): Flow<List<AlarmHistoryEvent>> =
        MutableStateFlow(events.filter { it.scheduleId == scheduleId })

    override fun observeLatestAdjustments(): Flow<Map<Long, AlarmHistoryEvent>> = MutableStateFlow(
        events
            .filter { it.type == AlarmEventType.AUTO_ADJUSTED || it.type == AlarmEventType.ADJUSTMENT_APPLIED }
            .groupBy { it.scheduleId }
            .mapValues { it.value.last() },
    )

    override suspend fun recentTravelDurations(scheduleId: Long, limit: Int): List<Duration> = recentTravel

    override fun observeDismissals(scheduleId: Long, limit: Int): Flow<List<AlarmHistoryEvent>> =
        MutableStateFlow(events.filter { it.scheduleId == scheduleId && it.type == AlarmEventType.DISMISSED })
}

class FakeMetricsRepository : MetricsRepository {
    val recorded = mutableListOf<MetricType>()
    override suspend fun record(type: MetricType, valueMillis: Long?) {
        recorded += type
    }

    override fun observeSummary(): Flow<MetricsSummary> = MutableStateFlow(MetricsSummary())
}

class FakeLocationRepository(
    var result: AppResult<GeoPoint> = AppResult.Success(GeoPoint(37.6, 127.1)),
) : LocationRepository {
    override fun hasLocationPermission(): Boolean = result is AppResult.Success
    override suspend fun getCurrentLocation(): AppResult<GeoPoint> = result
}

class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state
    override suspend fun current(): UserPreferences = state.value
    override suspend fun setDefaultPreparationMinutes(minutes: Int) = state.update { it.copy(defaultPreparationMinutes = minutes) }
    override suspend fun setDefaultBufferMinutes(minutes: Int) = state.update { it.copy(defaultBufferMinutes = minutes) }
    override suspend fun setAutoAdjustment(enabled: Boolean) = state.update { it.copy(autoAdjustment = enabled) }
    override suspend fun setTrafficNotifications(enabled: Boolean) = state.update { it.copy(trafficNotificationsEnabled = enabled) }
    override suspend fun setVibration(enabled: Boolean) = state.update { it.copy(vibrationEnabled = enabled) }
    override suspend fun setAlarmSound(sound: AlarmSound) = state.update { it.copy(alarmSound = sound) }
    override suspend fun setCustomSound(uri: String, title: String?) =
        state.update { it.copy(alarmSound = AlarmSound.CUSTOM, customSoundUri = uri, customSoundTitle = title) }
    override suspend fun setAlarmVolume(percent: Int) =
        state.update { it.copy(alarmVolumePercent = percent.coerceIn(UserPreferences.ALARM_VOLUME_RANGE)) }
    override suspend fun setGradualVolume(enabled: Boolean) = state.update { it.copy(gradualVolume = enabled) }
    override suspend fun setSnoozeMinutes(minutes: Int) = state.update { it.copy(snoozeMinutes = minutes) }
    override suspend fun setColorTheme(theme: ColorTheme) = state.update { it.copy(colorTheme = theme) }
    override suspend fun setLastPaywallPromptDay(epochDay: Long) = state.update { it.copy(lastPaywallPromptEpochDay = epochDay) }
    override suspend fun setOnboardingCompleted() = state.update { it.copy(onboardingCompleted = true) }
}

/** 결제·서버 없이 플랜을 바꿔 볼 수 있는 구독 저장소 */
class FakeSubscriptionRepository(
    initial: Entitlement = Entitlement.Free,
    private val time: TimeProvider? = null,
) : SubscriptionRepository {
    private val state = MutableStateFlow(initial)
    var failWith: AppError? = null
    val passes = mutableMapOf<String, GroupPass>()

    override val entitlement: Flow<Entitlement> = state
    override suspend fun current(): Entitlement = state.value

    /** 서버가 승인한 주문(주문번호 → 상품). 테스트에서 미리 넣어 둔다. */
    val paidOrders = mutableMapOf<String, SubscriptionProduct>()

    /** 테스트 준비용: 결제를 거치지 않고 PRO 상태로 만든다. */
    fun grantPro(product: SubscriptionProduct, autoRenew: Boolean = false) {
        val now = time?.now() ?: Instant.EPOCH
        state.value = Entitlement(
            plan = if (product == SubscriptionProduct.YEARLY) PlanType.PRO_YEARLY else PlanType.PRO_MONTHLY,
            startedAt = now,
            expiresAt = now.plus(Duration.ofDays(if (product == SubscriptionProduct.YEARLY) 365 else 30)),
            autoRenew = autoRenew,
        )
    }

    /** 결제창으로 보낸 뒤 확인하지 못한 주문 */
    var pendingOrder: String? = null
    private var nextOrder = 1

    override suspend fun startCheckout(product: SubscriptionProduct): AppResult<String> {
        failWith?.let { return AppResult.Failure(it) }
        val orderId = "ra_${nextOrder++}"
        pendingOrder = orderId
        return AppResult.Success("https://proxy.test/pay/checkout?orderId=$orderId")
    }

    override suspend fun pendingOrderId(): String? = pendingOrder

    override suspend fun completePayment(orderId: String): AppResult<Entitlement> {
        failWith?.let { return AppResult.Failure(it) }
        // 아직 결제 중인 주문은 기억해 두고, 승인된 주문만 PRO 로 바꾼 뒤 지운다.
        val product = paidOrders[orderId] ?: return AppResult.Failure(AppError.PurchaseFailed)
        grantPro(product)
        if (pendingOrder == orderId) pendingOrder = null
        return AppResult.Success(state.value)
    }

    override suspend fun verifyGroupPass(code: String): AppResult<GroupPass> {
        failWith?.let { return AppResult.Failure(it) }
        return passes[code]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.GroupPassInvalid)
    }

    override suspend fun redeemGroupPass(code: String): AppResult<Entitlement> {
        failWith?.let { return AppResult.Failure(it) }
        val pass = passes[code] ?: return AppResult.Failure(AppError.GroupPassInvalid)
        val entitlement = Entitlement(
            plan = PlanType.GROUP_PASS,
            expiresAt = pass.validUntil.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
            organization = pass.organization,
        )
        state.value = entitlement
        return AppResult.Success(entitlement)
    }

    override suspend fun clear() {
        state.value = Entitlement.Free
    }

    companion object {
        /** 자동 갱신 중인 PRO. 기존 "자동 조정" 시나리오 테스트의 기본값 */
        val PRO = Entitlement(plan = PlanType.PRO_YEARLY, autoRenew = true)

        fun hansungPass(until: LocalDate = LocalDate.of(2027, 2, 28)) =
            GroupPass("HSU-2026-WAKE7", "한성대학교", LocalDate.of(2026, 3, 1), until, remainingSeats = 128)
    }
}
