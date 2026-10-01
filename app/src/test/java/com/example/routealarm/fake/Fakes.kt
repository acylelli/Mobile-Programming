package com.example.routealarm.fake

import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.AlarmType
import com.example.routealarm.domain.model.GeoPoint
import com.example.routealarm.domain.model.MetricType
import com.example.routealarm.domain.model.MetricsSummary
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.RouteQueryResult
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.ThemeMode
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
import com.example.routealarm.domain.repository.TransitRefreshScheduler
import com.example.routealarm.domain.repository.TransitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
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
) : TransitRepository {
    var callCount = 0
        private set

    override suspend fun getRoutes(origin: Place, destination: Place, departureTime: Instant): AppResult<RouteQueryResult> {
        callCount++
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
                TransitSegment(SegmentType.SUBWAY, "8호선", origin.name, destination.name, total),
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

    override suspend fun deleteDemoSchedules(): AppResult<Unit> {
        schedules.update { map -> map.filterValues { !it.isDemo } }
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

    override suspend fun recentTravelDurations(scheduleId: Long, limit: Int): List<Duration> = recentTravel
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
    override suspend fun setSnoozeMinutes(minutes: Int) = state.update { it.copy(snoozeMinutes = minutes) }
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setOnboardingCompleted() = state.update { it.copy(onboardingCompleted = true) }
    override suspend fun setDemoSeeded() = state.update { it.copy(demoSeeded = true) }
    override suspend fun setDemoExtraDelay(minutes: Int) = state.update { it.copy(demo = it.demo.copy(extraDelayMinutes = minutes)) }
    override suspend fun setDemoNetworkFailure(enabled: Boolean) =
        state.update { it.copy(demo = it.demo.copy(simulateNetworkFailure = enabled)) }
}
