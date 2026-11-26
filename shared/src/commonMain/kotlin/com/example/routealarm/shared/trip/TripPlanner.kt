package com.example.routealarm.shared.trip

import com.example.routealarm.shared.AlarmEngine
import com.example.routealarm.shared.EngineInput
import com.example.routealarm.shared.EngineResult
import com.example.routealarm.shared.InvalidReason
import com.example.routealarm.shared.PlanTimes
import com.example.routealarm.shared.Precipitation
import com.example.routealarm.shared.WeatherRules
import com.example.routealarm.shared.api.ProxyClient
import com.example.routealarm.shared.api.RouteDto
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** 출발지·도착지 */
data class TripPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    /** 역·정류장이면 지나는 노선(검색 결과 둘째 줄 "서울 강남구 | 2호선, 신분당선") */
    val lines: List<String> = emptyList(),
)

/** 경로 한 구간(화면 표시용) */
data class TripSegment(
    /** WALK, BUS, SUBWAY, TRAIN, ETC */
    val mode: String,
    val lineName: String?,
    val from: String,
    val to: String,
    val minutes: Int,
    val arrivalMessage: String?,
    /** 탑승 구간 좌표를 [위도, 경도, 위도, 경도 …] 로 편 것(Swift 에서 쓰기 쉽게). 도보는 빈 목록 */
    val points: List<Double> = emptyList(),
)

/** 날씨 반영 결과 */
data class TripWeather(val precipitation: String, val probability: Int, val extraMinutes: Int, val umbrella: Boolean)

/**
 * 계산 결과. Swift 에서 쓰기 쉽게 시각은 epoch 밀리초(Long)로 둔다.
 */
data class TripPlan(
    val targetArrivalMillis: Long,
    val wakeUpMillis: Long,
    val departureMillis: Long,
    val estimatedArrivalMillis: Long,
    val travelMinutes: Int,
    val safetyBufferMinutes: Int,
    val delayMinutes: Int,
    val transferCount: Int,
    /** 첫 승차 전 대기(분) */
    val waitMinutes: Int,
    /** 환승 이동(분, 모든 환승 합) */
    val transferMinutes: Int,
    /** 실시간 교통 정보로 계산했는지. false 면 서버 캐시 또는 기본 이동시간(60분) */
    val isRealtime: Boolean,
    /** 교통 정보를 받지 못해(서버 연결 실패·경로 API 실패) 기본 이동시간으로 계산했는지 */
    val usedFallback: Boolean,
    /** 기상 시각은 지났지만 지금 출발하면 늦지 않음 */
    val wakeUpAlreadyPassed: Boolean,
    /** 지금 출발해도 늦음 */
    val isLate: Boolean,
    val lateByMinutes: Long,
    val segments: List<TripSegment>,
    val weather: TripWeather?,
)

/** 성공이면 plan, 실패면 error(사용자에게 보여 줄 한국어 문장) */
data class TripPlanResult(val plan: TripPlan?, val error: String?)

/**
 * 출발지·도착지·도착 목표로 경로를 받아 기상 시각을 계산한다(iOS 앱의 계산 진입점).
 *
 * 1. 목표 시각 − (기본 이동시간 + 여유) 에 출발한다고 보고 서버에 경로를 묻는다.
 * 2. 가장 빠른 경로의 이동시간·지연으로 [AlarmEngine] 계산(안드로이드와 같은 규칙).
 * 3. 날씨를 켜 두었으면 출발 시각 예보로 비 +5분·눈 +10분.
 * 서버에 닿지 못하면 기본 이동시간 60분(실시간 아님 → 여유 +5분)으로 계산해 알람이 빠지지 않게 한다.
 */
class TripPlanner(private val client: ProxyClient = ProxyClient()) {

    @Throws(CancellationException::class)
    suspend fun plan(
        origin: TripPlace,
        destination: TripPlace,
        targetArrivalMillis: Long,
        preparationMinutes: Int,
        bufferMinutes: Int,
        useWeather: Boolean,
        nowMillis: Long,
        recentTravelMinutes: List<Int> = emptyList(),
    ): TripPlanResult {
        val target = Instant.fromEpochMilliseconds(targetArrivalMillis)
        val now = Instant.fromEpochMilliseconds(nowMillis)
        val guessDeparture = target - (AlarmEngine.FALLBACK_TRAVEL_MINUTES + bufferMinutes).minutes

        val response = runCatchingNetwork {
            client.routes(origin.latitude, origin.longitude, destination.latitude, destination.longitude, maxOf(guessDeparture, now))
        }
        val route: RouteDto? = response?.routes?.minByOrNull { it.totalMinutes }
        if (response != null && route == null) return TripPlanResult(null, "두 장소 사이의 대중교통 경로를 찾지 못했어요.")
        val travelMinutes = route?.totalMinutes?.toLong() ?: AlarmEngine.FALLBACK_TRAVEL_MINUTES
        val isRealtime = response?.realtime ?: false

        val departureForWeather = target - (travelMinutes + bufferMinutes).minutes
        val weather = if (useWeather) {
            runCatchingNetwork { client.weather(origin.latitude, origin.longitude, maxOf(departureForWeather, now)) }?.let {
                val precipitation = Precipitation.entries.firstOrNull { p -> p.name == it.precipitation } ?: Precipitation.NONE
                TripWeather(
                    precipitation = precipitation.name,
                    probability = it.probability,
                    extraMinutes = WeatherRules.extraMinutes(precipitation),
                    umbrella = WeatherRules.needsUmbrella(precipitation, it.probability),
                )
            }
        } else {
            null
        }

        val result = AlarmEngine.plan(
            EngineInput(
                targetArrival = target,
                currentTravel = travelMinutes.minutes,
                delay = (route?.delayMinutes ?: 0).minutes,
                recentTravel = recentTravelMinutes.map { it.minutes },
                preparation = preparationMinutes.minutes,
                baseBufferMinutes = bufferMinutes,
                isRealtime = isRealtime,
                weatherExtraMinutes = weather?.extraMinutes ?: 0,
            ),
            now,
        )
        fun PlanTimes.toPlan(passed: Boolean, late: Boolean, lateBy: Long) = TripPlan(
            targetArrivalMillis = targetArrival.toEpochMilliseconds(),
            wakeUpMillis = wakeUp.toEpochMilliseconds(),
            departureMillis = departure.toEpochMilliseconds(),
            estimatedArrivalMillis = estimatedArrival.toEpochMilliseconds(),
            travelMinutes = this.travelMinutes,
            safetyBufferMinutes = safetyBufferMinutes,
            delayMinutes = route?.delayMinutes ?: 0,
            transferCount = route?.transferCount ?: 0,
            waitMinutes = route?.waitMinutes ?: 0,
            transferMinutes = route?.transferMinutes ?: 0,
            isRealtime = isRealtime,
            usedFallback = response == null,
            wakeUpAlreadyPassed = passed,
            isLate = late,
            lateByMinutes = lateBy,
            segments = route?.segments.orEmpty().map {
                TripSegment(it.mode, it.lineName, it.from, it.to, it.minutes, it.arrivalMessage, it.points.filter { p -> p.size >= 2 }.flatMap { p -> p.take(2) })
            },
            weather = weather,
        )
        return when (result) {
            is EngineResult.Invalid -> TripPlanResult(
                null,
                when (result.reason) {
                    InvalidReason.TARGET_IN_PAST -> "도착 시간이 이미 지났어요."
                    InvalidReason.NEGATIVE_DURATION -> "준비 시간과 여유 시간을 확인해 주세요."
                },
            )
            is EngineResult.OnTime -> TripPlanResult(result.times.toPlan(result.wakeUpAlreadyPassed, false, 0), null)
            is EngineResult.TooLate -> TripPlanResult(result.times.toPlan(true, true, result.lateByMinutes), null)
        }
    }

    private suspend fun <T> runCatchingNetwork(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/**
 * "만약 15분 늦어진다면?"(안드로이드 PreviewDelayScenarioUseCase): 이동시간과 지연을 15분 늘려 다시 계산한 기상 시각.
 * 지금 시각에 따라 결과가 달라지지 않게(지각 처리) 아주 이른 시각을 기준으로 계산한다.
 */
object DelayScenario {
    const val DEFAULT_DELAY_MINUTES = 15

    fun wakeUpMillis(plan: TripPlan, preparationMinutes: Int, bufferMinutes: Int, delayMinutes: Int = DEFAULT_DELAY_MINUTES): Long? {
        val result = AlarmEngine.plan(
            EngineInput(
                targetArrival = Instant.fromEpochMilliseconds(plan.targetArrivalMillis),
                currentTravel = (plan.travelMinutes + delayMinutes).minutes,
                delay = (plan.delayMinutes + delayMinutes).minutes,
                preparation = preparationMinutes.minutes,
                baseBufferMinutes = bufferMinutes,
                isRealtime = plan.isRealtime,
            ),
            Instant.fromEpochMilliseconds(0),
        )
        return (result as? EngineResult.OnTime)?.times?.wakeUp?.toEpochMilliseconds()
    }
}
