package com.example.routealarm.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 SQLite 에서 이전 버전 DB 를 만들고 마이그레이션한 뒤
 * Room 이 기대하는 스키마와 같은지(runMigrationsAndValidate), 데이터가 남았는지 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class RouteAlarmMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RouteAlarmDatabase::class.java)

    @Test
    fun migrate1To2_keepsUserDataAndRemovesDemoData() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(insertPlace(id = 1, name = "다산역", isDemo = 0))
            db.execSQL(insertPlace(id = 2, name = "예시 장소", isDemo = 1))
            db.execSQL(insertSchedule(id = 10, title = "평일 등교", isDemo = 0))
            db.execSQL(insertSchedule(id = 11, title = "예시 일정", isDemo = 1))
            db.execSQL(insertHistory(id = 100, scheduleId = 10))
            db.execSQL(insertHistory(id = 101, scheduleId = 11))
        }

        helper.runMigrationsAndValidate(DB, 2, true, Migrations.MIGRATION_1_2).use { db ->
            assertEquals(listOf("다산역"), db.strings("SELECT name FROM places"))
            assertEquals(listOf("평일 등교"), db.strings("SELECT title FROM schedules"))
            // 표를 새로 만들어 옮겨도 사용자 일정의 알람 기록은 남고, 예시 일정의 기록만 지워진다.
            assertEquals(listOf("100"), db.strings("SELECT id FROM alarm_history"))
        }
    }

    @Test
    fun migrate2To3_addsSkipHolidaysTurnedOn() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(insertPlace(id = 1, name = "다산역", isDemo = 0))
            db.execSQL(insertSchedule(id = 10, title = "평일 등교", isDemo = 0))
        }
        helper.runMigrationsAndValidate(DB, 2, true, Migrations.MIGRATION_1_2).close()

        helper.runMigrationsAndValidate(DB, 3, true, Migrations.MIGRATION_2_3).use { db ->
            assertEquals(listOf("1"), db.strings("SELECT skipHolidays FROM schedules"))
        }
    }

    @Test
    fun migrate3To4_addsScheduleSoundColumnsAsFollowSettings() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(insertPlace(id = 1, name = "다산역", isDemo = 0))
            db.execSQL(insertSchedule(id = 10, title = "평일 등교", isDemo = 0))
        }
        helper.runMigrationsAndValidate(DB, 3, true, Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3).close()

        helper.runMigrationsAndValidate(DB, 4, true, Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT soundType, soundUri, soundTitle, soundVolume FROM schedules").use { cursor ->
                cursor.moveToFirst()
                (0..3).forEach { assertEquals(true, cursor.isNull(it)) }
            }
        }
    }

    @Test
    fun migrate4To5_addsTravelObservationsTable() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(insertPlace(id = 1, name = "다산역", isDemo = 0))
            db.execSQL(insertSchedule(id = 10, title = "평일 등교", isDemo = 0))
        }
        helper.runMigrationsAndValidate(DB, 4, true, Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4).close()

        helper.runMigrationsAndValidate(DB, 5, true, Migrations.MIGRATION_4_5).use { db ->
            db.execSQL(
                "INSERT INTO travel_observations (scheduleId, occurrenceTarget, departureAt, travelMinutes, observedAt) VALUES (10, 1, 0, 55, 0)",
            )
            // 같은 회차를 다시 넣으면 고유 인덱스 때문에 덮어쓴다.
            db.execSQL(
                "INSERT OR REPLACE INTO travel_observations (scheduleId, occurrenceTarget, departureAt, travelMinutes, observedAt) VALUES (10, 1, 0, 61, 0)",
            )
            assertEquals(listOf("61"), db.strings("SELECT travelMinutes FROM travel_observations"))
            assertEquals(listOf("평일 등교"), db.strings("SELECT title FROM schedules"))
        }
    }

    @Test
    fun migrate5To6_addsPlanWeatherColumnsAsUnknown() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(insertPlace(id = 1, name = "다산역", isDemo = 0))
            db.execSQL(insertSchedule(id = 10, title = "평일 등교", isDemo = 0))
        }
        helper.runMigrationsAndValidate(
            DB, 5, true, Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5,
        ).close()

        helper.runMigrationsAndValidate(DB, 6, true, Migrations.MIGRATION_5_6).use { db ->
            db.query("SELECT plan_weatherPrecipitation, plan_weatherProbability, plan_weatherExtraMinutes, title FROM schedules").use { cursor ->
                cursor.moveToFirst()
                (0..2).forEach { assertEquals(true, cursor.isNull(it)) }
                assertEquals("평일 등교", cursor.getString(3))
            }
        }
    }

    @Test
    fun migratedDatabaseOpensWithRoomAndReadsSchedules() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(insertPlace(id = 1, name = "다산역", isDemo = 0))
            db.execSQL(insertSchedule(id = 10, title = "평일 등교", isDemo = 0))
        }

        // 앱과 같은 방식(파괴적 마이그레이션 없이 Migrations.ALL)으로 연다.
        val room = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RouteAlarmDatabase::class.java,
            DB,
        ).addMigrations(*Migrations.ALL).build()
        try {
            val schedule = runBlocking { room.scheduleDao().get(10) }
            assertEquals("평일 등교", schedule?.title)
            assertEquals("한성대학교", schedule?.destination?.name)
        } finally {
            room.close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.strings(sql: String): List<String> =
        query(sql).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun insertPlace(id: Long, name: String, isDemo: Int) =
        "INSERT INTO places (id, name, address, latitude, longitude, type, isDemo, createdAt) " +
            "VALUES ($id, '$name', '경기 남양주시', 37.62, 127.15, 'CUSTOM', $isDemo, 0)"

    private fun insertSchedule(id: Long, title: String, isDemo: Int) =
        "INSERT INTO schedules (id, title, useCurrentLocation, targetArrivalMinuteOfDay, preparationMinutes, bufferMinutes, " +
            "repeatDaysMask, oneTimeEpochDay, alarmMode, enabled, isDemo, createdAt, updatedAt, routeJson, pendingAdjustmentJson, " +
            "origin_name, origin_address, origin_latitude, origin_longitude, origin_type, " +
            "destination_name, destination_address, destination_latitude, destination_longitude, destination_type) " +
            "VALUES ($id, '$title', 0, 540, 30, 10, 31, NULL, 'WAKE_UP_AND_DEPARTURE', 1, $isDemo, 0, 0, NULL, NULL, " +
            "'다산역', '경기 남양주시', 37.62, 127.15, 'CUSTOM', '한성대학교', '서울 성북구', 37.58, 127.01, 'SCHOOL')"

    private fun insertHistory(id: Long, scheduleId: Long) =
        "INSERT INTO alarm_history (id, scheduleId, type, occurredAt) VALUES ($id, $scheduleId, 'CREATED', 0)"

    private companion object {
        const val DB = "migration-test.db"
    }
}
