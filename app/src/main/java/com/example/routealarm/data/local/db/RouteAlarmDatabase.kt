package com.example.routealarm.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaceEntity::class,
        ScheduleEntity::class,
        RouteCacheEntity::class,
        AlarmHistoryEntity::class,
        MetricEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RouteAlarmDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun routeCacheDao(): RouteCacheDao
    abstract fun alarmHistoryDao(): AlarmHistoryDao
    abstract fun metricDao(): MetricDao

    companion object {
        const val NAME = "route_alarm.db"
    }
}
