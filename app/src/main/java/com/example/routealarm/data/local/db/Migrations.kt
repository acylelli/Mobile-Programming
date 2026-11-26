package com.example.routealarm.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 스키마 마이그레이션. 앱을 업데이트해도 저장된 장소·알람·기록이 지워지지 않게 한다.
 *
 * 스키마(Entity)를 바꿀 때는
 * 1. [RouteAlarmDatabase] 의 version 을 1 올리고,
 * 2. 빌드해서 생긴 app/schemas/…/<새 버전>.json 을 함께 커밋하고,
 * 3. 여기에 이전 버전 → 새 버전 Migration 을 만들어 [ALL] 에 넣고,
 * 4. androidTest 의 RouteAlarmMigrationTest 에 그 버전 검증을 추가한다.
 * 파괴적 마이그레이션을 쓰지 않으므로 Migration 이 빠지면 데이터를 지우는 대신 앱이 열리지 않는다.
 * 그래서 MigrationCoverageTest(JVM 단위 테스트)가 빠진 버전을 빌드 단계에서 먼저 잡는다.
 */
object Migrations {

    /**
     * 1 → 2: 예시(가짜) 데이터를 없애면서 places·schedules 의 isDemo 열을 뺐다.
     * - 예시로 들어 있던 장소·일정(isDemo = 1)과 그 일정의 알람 기록은 지운다.
     * - 사용자가 만든 장소·일정·알람 기록은 그대로 옮긴다.
     * minSdk 26 의 SQLite 는 DROP COLUMN 을 지원하지 않아서 새 표를 만들어 옮긴 뒤 이름을 바꾼다.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM alarm_history WHERE scheduleId IN (SELECT id FROM schedules WHERE isDemo = 1)")
            db.execSQL("DELETE FROM schedules WHERE isDemo = 1")
            db.execSQL("DELETE FROM places WHERE isDemo = 1")

            db.execSQL("CREATE TABLE IF NOT EXISTS `places_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `address` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `type` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("INSERT INTO places_new (`id`, `name`, `address`, `latitude`, `longitude`, `type`, `createdAt`) SELECT `id`, `name`, `address`, `latitude`, `longitude`, `type`, `createdAt` FROM places")
            db.execSQL("DROP TABLE places")
            db.execSQL("ALTER TABLE places_new RENAME TO places")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `schedules_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `useCurrentLocation` INTEGER NOT NULL, `targetArrivalMinuteOfDay` INTEGER NOT NULL, `preparationMinutes` INTEGER NOT NULL, `bufferMinutes` INTEGER NOT NULL, `repeatDaysMask` INTEGER NOT NULL, `oneTimeEpochDay` INTEGER, `alarmMode` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `routeJson` TEXT, `pendingAdjustmentJson` TEXT, `origin_name` TEXT NOT NULL, `origin_address` TEXT NOT NULL, `origin_latitude` REAL NOT NULL, `origin_longitude` REAL NOT NULL, `origin_type` TEXT NOT NULL, `destination_name` TEXT NOT NULL, `destination_address` TEXT NOT NULL, `destination_latitude` REAL NOT NULL, `destination_longitude` REAL NOT NULL, `destination_type` TEXT NOT NULL, `plan_targetArrivalTime` INTEGER, `plan_estimatedArrivalTime` INTEGER, `plan_estimatedDepartureTime` INTEGER, `plan_wakeUpTime` INTEGER, `plan_travelMinutes` INTEGER, `plan_safetyBufferMinutes` INTEGER, `plan_delayMinutes` INTEGER, `plan_previousTravelMinutes` INTEGER, `plan_isRealtime` INTEGER, `plan_lastUpdatedAt` INTEGER)",
            )
            db.execSQL(
                "INSERT INTO schedules_new (`id`, `title`, `useCurrentLocation`, `targetArrivalMinuteOfDay`, `preparationMinutes`, `bufferMinutes`, `repeatDaysMask`, `oneTimeEpochDay`, `alarmMode`, `enabled`, `createdAt`, `updatedAt`, `routeJson`, `pendingAdjustmentJson`, `origin_name`, `origin_address`, `origin_latitude`, `origin_longitude`, `origin_type`, `destination_name`, `destination_address`, `destination_latitude`, `destination_longitude`, `destination_type`, `plan_targetArrivalTime`, `plan_estimatedArrivalTime`, `plan_estimatedDepartureTime`, `plan_wakeUpTime`, `plan_travelMinutes`, `plan_safetyBufferMinutes`, `plan_delayMinutes`, `plan_previousTravelMinutes`, `plan_isRealtime`, `plan_lastUpdatedAt`) " +
                    "SELECT `id`, `title`, `useCurrentLocation`, `targetArrivalMinuteOfDay`, `preparationMinutes`, `bufferMinutes`, `repeatDaysMask`, `oneTimeEpochDay`, `alarmMode`, `enabled`, `createdAt`, `updatedAt`, `routeJson`, `pendingAdjustmentJson`, `origin_name`, `origin_address`, `origin_latitude`, `origin_longitude`, `origin_type`, `destination_name`, `destination_address`, `destination_latitude`, `destination_longitude`, `destination_type`, `plan_targetArrivalTime`, `plan_estimatedArrivalTime`, `plan_estimatedDepartureTime`, `plan_wakeUpTime`, `plan_travelMinutes`, `plan_safetyBufferMinutes`, `plan_delayMinutes`, `plan_previousTravelMinutes`, `plan_isRealtime`, `plan_lastUpdatedAt` FROM schedules",
            )
            db.execSQL("DROP TABLE schedules")
            db.execSQL("ALTER TABLE schedules_new RENAME TO schedules")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedules_enabled` ON `schedules` (`enabled`)")
        }
    }

    /** 앱이 아는 모든 마이그레이션. 버전을 올릴 때마다 여기에 더한다. */
    /** 2 → 3: 일정에 "공휴일에는 울리지 않기"(skipHolidays)를 추가했다. 기존 반복 일정도 켜진 상태로 시작한다. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE schedules ADD COLUMN skipHolidays INTEGER NOT NULL DEFAULT 1")
        }
    }

    /** 3 → 4: 일정 전용 알람음·볼륨 열을 추가했다. 기존 일정은 모두 null(설정 따르기)이다. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE schedules ADD COLUMN soundType TEXT")
            db.execSQL("ALTER TABLE schedules ADD COLUMN soundUri TEXT")
            db.execSQL("ALTER TABLE schedules ADD COLUMN soundTitle TEXT")
            db.execSQL("ALTER TABLE schedules ADD COLUMN soundVolume INTEGER")
        }
    }

    /** 4 → 5: 회차별 실제 이동시간 표(travel_observations)를 추가했다. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `travel_observations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`scheduleId` INTEGER NOT NULL, `occurrenceTarget` INTEGER NOT NULL, `departureAt` INTEGER NOT NULL, " +
                    "`travelMinutes` INTEGER NOT NULL, `observedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_travel_observations_scheduleId_occurrenceTarget` " +
                    "ON `travel_observations` (`scheduleId`, `occurrenceTarget`)",
            )
        }
    }

    /** 5 → 6: 계산에 반영한 날씨(강수형태·강수확률·추가 여유) 열을 plan 에 추가했다. 기존 계획은 null(날씨 모름). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE schedules ADD COLUMN plan_weatherPrecipitation TEXT")
            db.execSQL("ALTER TABLE schedules ADD COLUMN plan_weatherProbability INTEGER")
            db.execSQL("ALTER TABLE schedules ADD COLUMN plan_weatherExtraMinutes INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
