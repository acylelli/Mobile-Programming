package com.example.routealarm.data.mapper

import com.example.routealarm.data.local.db.AlarmHistoryEntity
import com.example.routealarm.data.local.db.PlaceEntity
import com.example.routealarm.data.local.db.PlaceSnapshot
import com.example.routealarm.data.local.db.PlanColumns
import com.example.routealarm.data.local.db.ScheduleEntity
import com.example.routealarm.domain.model.AlarmEventType
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.AlarmPlan
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.model.Schedule
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

fun PlaceEntity.toDomain() = Place(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    type = enumValueOrDefault(type, PlaceType.CUSTOM),
    isDemo = isDemo,
)

fun Place.toEntity(createdAt: Instant) = PlaceEntity(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    type = type.name,
    isDemo = isDemo,
    createdAt = createdAt.toEpochMilli(),
)

private fun Place.toSnapshot() = PlaceSnapshot(name, address, latitude, longitude, type.name)

private fun PlaceSnapshot.toDomain() = Place(
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    type = enumValueOrDefault(type, PlaceType.CUSTOM),
)

/** 요일 집합 ↔ 비트마스크 (월=bit0 ... 일=bit6) */
object DayMask {
    fun encode(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
    fun decode(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}

private const val MINUTES_PER_HOUR = 60

class ScheduleEntityMapper(private val json: Json) {

    fun toDomain(entity: ScheduleEntity): Schedule {
        val route = entity.routeJson?.let { runCatching { json.decodeFromString<StoredRoute>(it).toDomain() }.getOrNull() }
        return Schedule(
            id = entity.id,
            title = entity.title,
            origin = entity.origin.toDomain(),
            destination = entity.destination.toDomain(),
            useCurrentLocation = entity.useCurrentLocation,
            targetArrivalTime = LocalTime.of(
                entity.targetArrivalMinuteOfDay / MINUTES_PER_HOUR,
                entity.targetArrivalMinuteOfDay % MINUTES_PER_HOUR,
            ),
            preparationMinutes = entity.preparationMinutes,
            bufferMinutes = entity.bufferMinutes,
            repeatDays = DayMask.decode(entity.repeatDaysMask),
            oneTimeDate = entity.oneTimeEpochDay?.let(LocalDate::ofEpochDay),
            alarmMode = enumValueOrDefault(entity.alarmMode, AlarmMode.WAKE_UP_AND_DEPARTURE),
            enabled = entity.enabled,
            isDemo = entity.isDemo,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt),
            plan = entity.plan?.toDomain(route),
            pendingAdjustment = entity.pendingAdjustmentJson?.let {
                runCatching { json.decodeFromString<StoredPendingAdjustment>(it).toDomain() }.getOrNull()
            },
        )
    }

    fun toEntity(schedule: Schedule) = ScheduleEntity(
        id = schedule.id,
        title = schedule.title,
        origin = schedule.origin.toSnapshot(),
        destination = schedule.destination.toSnapshot(),
        useCurrentLocation = schedule.useCurrentLocation,
        targetArrivalMinuteOfDay = schedule.targetArrivalTime.hour * MINUTES_PER_HOUR +
            schedule.targetArrivalTime.minute,
        preparationMinutes = schedule.preparationMinutes,
        bufferMinutes = schedule.bufferMinutes,
        repeatDaysMask = DayMask.encode(schedule.repeatDays),
        oneTimeEpochDay = schedule.oneTimeDate?.toEpochDay(),
        alarmMode = schedule.alarmMode.name,
        enabled = schedule.enabled,
        isDemo = schedule.isDemo,
        createdAt = schedule.createdAt.toEpochMilli(),
        updatedAt = schedule.updatedAt.toEpochMilli(),
        plan = schedule.plan?.toColumns(),
        routeJson = schedule.plan?.route?.let { json.encodeToString(StoredRoute.serializer(), it.toStored()) },
        pendingAdjustmentJson = schedule.pendingAdjustment?.let {
            json.encodeToString(StoredPendingAdjustment.serializer(), it.toStored())
        },
    )

    private fun AlarmPlan.toColumns() = PlanColumns(
        targetArrivalTime = targetArrival.toEpochMilli(),
        estimatedArrivalTime = estimatedArrival.toEpochMilli(),
        estimatedDepartureTime = departure.toEpochMilli(),
        wakeUpTime = wakeUp.toEpochMilli(),
        travelMinutes = travelMinutes,
        safetyBufferMinutes = safetyBufferMinutes,
        delayMinutes = delayMinutes,
        previousTravelMinutes = previousTravelMinutes,
        isRealtime = isRealtime,
        lastUpdatedAt = lastUpdatedAt.toEpochMilli(),
    )

    private fun PlanColumns.toDomain(route: com.example.routealarm.domain.model.TransitRoute?) = AlarmPlan(
        targetArrival = Instant.ofEpochMilli(targetArrivalTime),
        estimatedArrival = Instant.ofEpochMilli(estimatedArrivalTime),
        departure = Instant.ofEpochMilli(estimatedDepartureTime),
        wakeUp = Instant.ofEpochMilli(wakeUpTime),
        travelMinutes = travelMinutes,
        safetyBufferMinutes = safetyBufferMinutes,
        delayMinutes = delayMinutes,
        previousTravelMinutes = previousTravelMinutes,
        isRealtime = isRealtime,
        lastUpdatedAt = Instant.ofEpochMilli(lastUpdatedAt),
        route = route,
    )
}

fun AlarmHistoryEntity.toDomain() = AlarmHistoryEvent(
    id = id,
    scheduleId = scheduleId,
    type = enumValueOrDefault(type, AlarmEventType.RECALCULATED),
    occurredAt = Instant.ofEpochMilli(occurredAt),
    previousWakeUp = previousWakeUp?.let(Instant::ofEpochMilli),
    newWakeUp = newWakeUp?.let(Instant::ofEpochMilli),
    travelMinutes = travelMinutes,
    isRealtime = isRealtime,
)

fun AlarmHistoryEvent.toEntity() = AlarmHistoryEntity(
    id = id,
    scheduleId = scheduleId,
    type = type.name,
    occurredAt = occurredAt.toEpochMilli(),
    previousWakeUp = previousWakeUp?.toEpochMilli(),
    newWakeUp = newWakeUp?.toEpochMilli(),
    travelMinutes = travelMinutes,
    isRealtime = isRealtime,
)
