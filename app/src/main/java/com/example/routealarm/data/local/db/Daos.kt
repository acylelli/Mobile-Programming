package com.example.routealarm.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY isDemo ASC, createdAt ASC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun get(id: Long): PlaceEntity?

    @Upsert
    suspend fun upsert(place: PlaceEntity): Long

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY isDemo ASC, targetArrivalMinuteOfDay ASC")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    fun observe(id: Long): Flow<ScheduleEntity?>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun get(id: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun getEnabled(): List<ScheduleEntity>

    @Upsert
    suspend fun upsert(schedule: ScheduleEntity): Long

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM schedules WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface RouteCacheDao {
    @Query("SELECT * FROM route_cache WHERE cacheKey = :key")
    suspend fun get(key: String): RouteCacheEntity?

    @Upsert
    suspend fun upsert(entity: RouteCacheEntity)

    @Query("DELETE FROM route_cache WHERE fetchedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface AlarmHistoryDao {
    @Insert
    suspend fun insert(entity: AlarmHistoryEntity)

    @Query("SELECT * FROM alarm_history WHERE scheduleId = :scheduleId ORDER BY occurredAt DESC LIMIT :limit")
    fun observe(scheduleId: Long, limit: Int): Flow<List<AlarmHistoryEntity>>

    /** 실시간 데이터로 계산된 이동시간만 안정화 입력값으로 사용한다. */
    @Query(
        """
        SELECT travelMinutes FROM alarm_history
        WHERE scheduleId = :scheduleId AND travelMinutes IS NOT NULL AND isRealtime = 1
          AND type IN ('CREATED', 'RECALCULATED', 'AUTO_ADJUSTED', 'ADJUSTMENT_APPLIED')
        ORDER BY occurredAt DESC LIMIT :limit
        """,
    )
    suspend fun recentTravelMinutes(scheduleId: Long, limit: Int): List<Int>

    @Query("DELETE FROM alarm_history WHERE occurredAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

data class MetricAggregate(val type: String, val count: Int, val averageValue: Double?)

@Dao
interface MetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MetricEventEntity)

    @Query("SELECT type, COUNT(*) AS count, AVG(value_millis) AS averageValue FROM metric_events GROUP BY type")
    fun observeAggregates(): Flow<List<MetricAggregate>>

    @Query("DELETE FROM metric_events WHERE occurredAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
