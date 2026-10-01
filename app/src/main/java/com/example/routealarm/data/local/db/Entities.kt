package com.example.routealarm.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val isDemo: Boolean,
    val createdAt: Long,
)

/**
 * 일정에 저장되는 장소 스냅샷.
 *
 * 설계 이유: 일정이 places 테이블을 FK 로 참조하면 사용자가 즐겨찾기 장소를 지우거나 고쳤을 때
 * 이미 설정된 알람의 출발지/도착지가 조용히 바뀌거나 사라진다. 일정 생성 시점의 값을 복사해 둔다.
 */
data class PlaceSnapshot(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
)

/** 계산된 최신 알람 정보. 모든 컬럼이 null 이면 아직 계산 전이다. */
data class PlanColumns(
    val targetArrivalTime: Long,
    val estimatedArrivalTime: Long,
    val estimatedDepartureTime: Long,
    val wakeUpTime: Long,
    val travelMinutes: Int,
    val safetyBufferMinutes: Int,
    val delayMinutes: Int,
    val previousTravelMinutes: Int?,
    val isRealtime: Boolean,
    val lastUpdatedAt: Long,
)

@Entity(tableName = "schedules", indices = [Index("enabled")])
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @Embedded(prefix = "origin_") val origin: PlaceSnapshot,
    @Embedded(prefix = "destination_") val destination: PlaceSnapshot,
    val useCurrentLocation: Boolean,
    /** 자정 기준 분(0..1439). LocalTime 을 문자열로 저장하지 않는다. */
    val targetArrivalMinuteOfDay: Int,
    val preparationMinutes: Int,
    val bufferMinutes: Int,
    /** 월=1 ... 일=64 비트마스크 */
    val repeatDaysMask: Int,
    val oneTimeEpochDay: Long?,
    val alarmMode: String,
    val enabled: Boolean,
    val isDemo: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    @Embedded(prefix = "plan_") val plan: PlanColumns?,
    /** 경로 타임라인 표시용 스냅샷(JSON) */
    val routeJson: String?,
    /** 사용자 확인 대기 중인 변경안(JSON). 드물게만 존재하므로 컬럼을 펼치지 않는다. */
    val pendingAdjustmentJson: String?,
)

/**
 * 네트워크 실패 시 사용할 경로 캐시(Offline First). 좌표는 약 100m 단위로 반올림한 키로만 저장한다.
 */
@Entity(tableName = "route_cache")
data class RouteCacheEntity(
    @PrimaryKey val cacheKey: String,
    val routesJson: String,
    val fetchedAt: Long,
)

@Entity(
    tableName = "alarm_history",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduleId"), Index("occurredAt")],
)
data class AlarmHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val type: String,
    val occurredAt: Long,
    val previousWakeUp: Long?,
    val newWakeUp: Long?,
    val travelMinutes: Int?,
    val isRealtime: Boolean?,
)

@Entity(tableName = "metric_events", indices = [Index("type")])
data class MetricEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "value_millis") val valueMillis: Long?,
    val occurredAt: Long,
)
