package com.example.routealarm.data.mapper

import com.example.routealarm.data.remote.dto.PlaceDto
import com.example.routealarm.data.remote.dto.RouteDto
import com.example.routealarm.data.remote.dto.RouteResponseDto
import com.example.routealarm.data.remote.dto.SegmentDto
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.model.SegmentType
import com.example.routealarm.domain.model.TransitRoute
import com.example.routealarm.domain.model.TransitSegment
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * 네트워크 DTO → Domain 모델 변환. API 데이터는 반드시 여기서 Domain 모델로 바뀐 뒤에만 앱 안으로 들어간다.
 * 잘못된 항목(음수 시간, 파싱 불가 시각)은 앱 전체를 실패시키지 않고 해당 경로만 버린다.
 */
object TransitDtoMapper {

    fun toDomain(response: RouteResponseDto): List<TransitRoute> = response.routes.mapNotNull(::toDomainOrNull)

    private fun toDomainOrNull(dto: RouteDto): TransitRoute? {
        if (dto.totalMinutes < 0) return null
        val departure = parseInstant(dto.departureTime) ?: return null
        val arrival = parseInstant(dto.arrivalTime) ?: departure.plus(minutes(dto.totalMinutes))
        return TransitRoute(
            totalDuration = minutes(dto.totalMinutes),
            walkingDuration = minutes(dto.walkMinutes),
            waitingDuration = minutes(dto.waitMinutes),
            transferDuration = minutes(dto.transferMinutes),
            departureTime = departure,
            arrivalTime = arrival,
            transferCount = dto.transferCount.coerceAtLeast(0),
            segments = dto.segments.map(::toDomain),
            delay = minutes(dto.delayMinutes),
        )
    }

    private fun toDomain(dto: SegmentDto) = TransitSegment(
        type = when (dto.mode.uppercase()) {
            "WALK" -> SegmentType.WALK
            "BUS" -> SegmentType.BUS
            "SUBWAY" -> SegmentType.SUBWAY
            "TRAIN" -> SegmentType.TRAIN
            else -> SegmentType.ETC
        },
        lineName = dto.lineName,
        startName = dto.from,
        endName = dto.to,
        duration = minutes(dto.minutes),
        arrivalInfo = dto.arrivalMessage,
    )

    fun toDomain(dto: PlaceDto) = Place(
        name = dto.name,
        address = dto.address,
        latitude = dto.latitude,
        longitude = dto.longitude,
        type = PlaceType.CUSTOM,
    )

    private fun minutes(value: Int): Duration = Duration.ofMinutes(value.coerceAtLeast(0).toLong())

    private fun parseInstant(value: String): Instant? =
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
}
