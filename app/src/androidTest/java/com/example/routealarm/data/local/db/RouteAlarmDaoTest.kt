package com.example.routealarm.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.routealarm.data.mapper.ScheduleEntityMapper
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.GeoPoint
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.domain.model.TransitSegment
import com.example.routealarm.fake.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant

/** 실제 SQLite(메모리 DB)에서 DAO 쿼리와 Entity 변환을 확인한다. */
@RunWith(AndroidJUnit4::class)
class RouteAlarmDaoTest {

    private lateinit var db: RouteAlarmDatabase
    private val mapper = ScheduleEntityMapper(Json { ignoreUnknownKeys = true })
    private val t0 = Instant.parse("2026-11-26T00:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RouteAlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertSchedule(enabled: Boolean = true, plan: AlarmPlan? = null): Long =
        db.scheduleDao().upsert(mapper.toEntity(TestData.schedule(repeatDays = setOf(DayOfWeek.MONDAY), enabled = enabled).copy(plan = plan)))

    private fun history(scheduleId: Long, type: String, minutesAfter: Long, travel: Int? = null, realtime: Boolean? = null, wake: Instant? = null) =
        AlarmHistoryEntity(
            scheduleId = scheduleId, type = type, occurredAt = t0.plusSeconds(minutesAfter * 60).toEpochMilli(),
            previousWakeUp = null, newWakeUp = wake?.toEpochMilli(), travelMinutes = travel, isRealtime = realtime,
        )

    @Test
    fun scheduleWithPlanAndRoutePoints_roundTripsThroughEntity() = runTest {
        val route = TransitRoute(
            totalDuration = Duration.ofMinutes(54), walkingDuration = Duration.ofMinutes(8), waitingDuration = Duration.ofMinutes(3),
            transferDuration = Duration.ZERO, departureTime = t0, arrivalTime = t0.plusSeconds(54 * 60), transferCount = 1,
            segments = listOf(
                TransitSegment(SegmentType.BUS, "76번", "A", "B", Duration.ofMinutes(27), points = listOf(GeoPoint(37.62, 127.15), GeoPoint(37.61, 127.08))),
                TransitSegment(SegmentType.SUBWAY, "6호선", "B", "C", Duration.ofMinutes(16)),
            ),
            delay = Duration.ofMinutes(4),
        )
        val plan = AlarmPlan(
            targetArrival = t0.plusSeconds(3600), estimatedArrival = t0.plusSeconds(3000), departure = t0, wakeUp = t0.minusSeconds(1800),
            travelMinutes = 54, safetyBufferMinutes = 10, delayMinutes = 4, previousTravelMinutes = 58, isRealtime = true,
            lastUpdatedAt = t0, route = route,
        )
        val id = insertSchedule(plan = plan)

        val loaded = mapper.toDomain(db.scheduleDao().get(id)!!)

        assertEquals(plan, loaded.plan)
        assertEquals(setOf(DayOfWeek.MONDAY), loaded.repeatDays)
        assertEquals("한성대학교", loaded.destination.name)
    }

    @Test
    fun getEnabled_returnsOnlyEnabledSchedules() = runTest {
        val on = insertSchedule(enabled = true)
        insertSchedule(enabled = false)

        assertEquals(listOf(on), db.scheduleDao().getEnabled().map { it.id })
    }

    @Test
    fun deletingSchedule_removesItsAlarmHistory() = runTest {
        val keep = insertSchedule()
        val gone = insertSchedule()
        db.alarmHistoryDao().insert(history(keep, "CREATED", 0))
        db.alarmHistoryDao().insert(history(gone, "CREATED", 0))

        db.scheduleDao().delete(gone)

        assertEquals(1, db.alarmHistoryDao().observe(keep, 10).first().size)
        assertEquals(0, db.alarmHistoryDao().observe(gone, 10).first().size)
    }

    @Test
    fun recentTravelMinutes_usesOnlyRealtimeCalculationsNewestFirst() = runTest {
        val id = insertSchedule()
        val dao = db.alarmHistoryDao()
        dao.insert(history(id, "CREATED", 0, travel = 60, realtime = true))
        dao.insert(history(id, "RECALCULATED", 10, travel = 55, realtime = false)) // 캐시 값은 제외
        dao.insert(history(id, "FIRED", 20, travel = 50, realtime = true)) // 계산이 아닌 이벤트는 제외
        dao.insert(history(id, "AUTO_ADJUSTED", 30, travel = 65, realtime = true))
        dao.insert(history(id, "RECALCULATED", 40, travel = 62, realtime = true))

        assertEquals(listOf(62, 65), dao.recentTravelMinutes(id, limit = 2))
    }

    @Test
    fun latestAdjustments_returnsNewestAdjustmentPerSchedule() = runTest {
        val a = insertSchedule()
        val b = insertSchedule()
        val dao = db.alarmHistoryDao()
        dao.insert(history(a, "AUTO_ADJUSTED", 0, travel = 60))
        dao.insert(history(a, "ADJUSTMENT_APPLIED", 10, travel = 70))
        dao.insert(history(a, "RECALCULATED", 20, travel = 80))
        dao.insert(history(b, "AUTO_ADJUSTED", 5, travel = 50))

        val latest = dao.observeLatestAdjustments().first().associate { it.scheduleId to it.travelMinutes }

        assertEquals(mapOf(a to 70, b to 50), latest)
    }

    @Test
    fun dismissals_returnOnlyDismissedEventsWithPlannedWakeUp() = runTest {
        val id = insertSchedule()
        val dao = db.alarmHistoryDao()
        dao.insert(history(id, "DISMISSED", 0, wake = t0))
        dao.insert(history(id, "DISMISSED", 5)) // 예정 기상 시각 없음
        dao.insert(history(id, "FIRED", 10, wake = t0))

        val dismissals = dao.observeDismissals(id, 10).first()

        assertEquals(listOf("DISMISSED"), dismissals.map { it.type })
    }

    @Test
    fun deleteOlderThan_removesOldHistoryOnly() = runTest {
        val id = insertSchedule()
        val dao = db.alarmHistoryDao()
        dao.insert(history(id, "CREATED", 0))
        dao.insert(history(id, "RECALCULATED", 60))

        dao.deleteOlderThan(t0.plusSeconds(30 * 60).toEpochMilli())

        assertEquals(listOf("RECALCULATED"), dao.observe(id, 10).first().map { it.type })
        assertNull(dao.observe(id, 10).first().firstOrNull { it.type == "CREATED" })
    }
}
