import Foundation
import Shared

/// 출발지·도착지
struct Place: Codable, Hashable {
    var name: String
    var address: String
    var latitude: Double
    var longitude: Double
    /** 역·정류장이면 지나는 노선 */
    var lines: [String]?

    init(name: String, address: String, latitude: Double, longitude: Double, lines: [String]? = nil) {
        self.name = name
        self.address = address
        self.latitude = latitude
        self.longitude = longitude
        self.lines = lines
    }

    init(_ place: TripPlace) {
        self.init(name: place.name, address: place.address, latitude: place.latitude, longitude: place.longitude, lines: place.lines)
    }

    var shared: TripPlace { TripPlace(name: name, address: address, latitude: latitude, longitude: longitude, lines: lines ?? []) }

    /// 같은 곳인지(이름·좌표가 거의 같으면)
    func isSame(_ other: Place?) -> Bool {
        guard let other else { return false }
        return abs(latitude - other.latitude) < 0.0002 && abs(longitude - other.longitude) < 0.0002
    }

    /// 검색 결과·최근 검색 둘째 줄: 노선이 있으면 "서울 강남구 | 2호선, 신분당선", 없으면 주소
    var subtitle: String? {
        if let lines, !lines.isEmpty {
            return "\(address.split(separator: " ").prefix(2).joined(separator: " ")) | \(lines.joined(separator: ", "))"
        }
        return address.isEmpty ? nil : address
    }
}

struct Segment: Codable, Hashable {
    var mode: String
    var lineName: String?
    var from: String
    var to: String
    var minutes: Int
    var arrivalMessage: String?
    /// 탑승 구간 좌표 [위도, 경도, …](지도 선)
    var points: [Double]?
}

struct WeatherNote: Codable, Hashable {
    var precipitation: String
    var probability: Int
    var extraMinutes: Int
    var umbrella: Bool
}

/// shared 의 TripPlan 을 저장 가능한 형태로 옮긴 것
struct SavedPlan: Codable, Hashable {
    var targetArrival: Date
    var wakeUp: Date
    var departure: Date
    var estimatedArrival: Date
    var travelMinutes: Int
    var bufferMinutes: Int
    var delayMinutes: Int
    var transferCount: Int
    var waitMinutes: Int
    var transferMinutes: Int
    var isRealtime: Bool
    var usedFallback: Bool
    var isLate: Bool
    var lateByMinutes: Int
    var segments: [Segment]
    var weather: WeatherNote?
    var calculatedAt: Date

    init(_ plan: TripPlan, calculatedAt: Date) {
        targetArrival = Date(millis: plan.targetArrivalMillis)
        wakeUp = Date(millis: plan.wakeUpMillis)
        departure = Date(millis: plan.departureMillis)
        estimatedArrival = Date(millis: plan.estimatedArrivalMillis)
        travelMinutes = Int(plan.travelMinutes)
        bufferMinutes = Int(plan.safetyBufferMinutes)
        delayMinutes = Int(plan.delayMinutes)
        transferCount = Int(plan.transferCount)
        waitMinutes = Int(plan.waitMinutes)
        transferMinutes = Int(plan.transferMinutes)
        isRealtime = plan.isRealtime
        usedFallback = plan.usedFallback
        isLate = plan.isLate
        lateByMinutes = Int(plan.lateByMinutes)
        segments = plan.segments.map {
            Segment(mode: $0.mode, lineName: $0.lineName, from: $0.from, to: $0.to, minutes: Int($0.minutes), arrivalMessage: $0.arrivalMessage,
                    points: $0.points.map { $0.doubleValue })
        }
        weather = plan.weather.map {
            WeatherNote(precipitation: $0.precipitation, probability: Int($0.probability), extraMinutes: Int($0.extraMinutes), umbrella: $0.umbrella)
        }
        self.calculatedAt = calculatedAt
    }

    /// 목표보다 얼마나 먼저 기상 알람이 울리는지(분). 다음 회차를 찾을 때 쓴다.
    var leadMinutes: Int { Int(targetArrival.timeIntervalSince(wakeUp) / 60) }

    /// shared 의 TripPlan 으로 되돌린다(노선도 계산용)
    var shared: TripPlan {
        TripPlan(
            targetArrivalMillis: targetArrival.millis, wakeUpMillis: wakeUp.millis, departureMillis: departure.millis,
            estimatedArrivalMillis: estimatedArrival.millis, travelMinutes: Int32(travelMinutes),
            safetyBufferMinutes: Int32(bufferMinutes), delayMinutes: Int32(delayMinutes), transferCount: Int32(transferCount),
            waitMinutes: Int32(waitMinutes), transferMinutes: Int32(transferMinutes), isRealtime: isRealtime,
            usedFallback: usedFallback, wakeUpAlreadyPassed: false, isLate: isLate, lateByMinutes: Int64(lateByMinutes),
            segments: segments.map {
                TripSegment(mode: $0.mode, lineName: $0.lineName, from: $0.from, to: $0.to, minutes: Int32($0.minutes), arrivalMessage: $0.arrivalMessage,
                            points: ($0.points ?? []).map { KotlinDouble(value: $0) })
            },
            weather: weather.map {
                TripWeather(precipitation: $0.precipitation, probability: Int32($0.probability), extraMinutes: Int32($0.extraMinutes), umbrella: $0.umbrella)
            }
        )
    }

    func routeLine(origin: String, destination: String) -> RouteLine {
        RouteLineBuilder.shared.build(plan: shared, originName: origin, destinationName: destination)
    }

    /// 안드로이드 TrafficStatus: 지연 3분 미만 "교통 정상", 3분 이상 "지연 +N분", 15분 이상 "심한 지연 +N분"
    var status: (text: String, icon: String, delayed: Bool) {
        if delayMinutes >= 15 { return ("심한 지연 +\(delayMinutes)분", "exclamationmark.triangle", true) }
        if delayMinutes >= 3 { return ("지연 +\(delayMinutes)분", "exclamationmark.triangle", true) }
        return ("교통 정상", "checkmark", false)
    }

    /// 실시간 교통 정보로 계산하지 못했는지(홈·상세의 오프라인 안내)
    var isOffline: Bool { usedFallback || !isRealtime }

    /// 목표 도착까지 남는 여유(분): 노선도 도착 지점 "여유 10분"
    var slackMinutes: Int { Int(targetArrival.timeIntervalSince(estimatedArrival) / 60) }

    /// 대표 노선(첫 탑승 구간)
    var mainLine: String? { segments.first { $0.mode != "WALK" }?.lineName }
}

/// 알람 일정 하나
struct Alarm: Codable, Identifiable, Hashable {
    var id = UUID()
    var title: String
    var origin: Place
    var destination: Place
    /// 도착 목표 시각
    var hour: Int
    var minute: Int
    /// 월=1, 화=2, 수=4 … 일=64. 0 이면 한 번
    var repeatMask: Int
    var preparationMinutes: Int
    var bufferMinutes: Int
    var useWeather: Bool
    var skipHolidays: Bool
    /// 출발지를 현재 위치로(계산할 때마다 한 번 조회)
    var useCurrentLocation: Bool? = nil
    /// 기상 알람만(출발 알림 없음)
    var wakeOnly: Bool? = nil
    /// 1회 일정의 날짜(nil 이면 가장 가까운 날)
    var oneTimeDate: Date? = nil
    var enabled: Bool = true
    var plan: SavedPlan?
    /// 알람 변경 추천(자동 조정이 꺼졌거나 변화가 커서 확인이 필요한 변경안)
    var pendingPlan: SavedPlan? = nil
    var pendingNeedsConfirmation: Bool? = nil
    /// 이 일정만의 알람음(없으면 설정 따르기)
    var sound: SoundSetting? = nil
    /// 실제로 쓸 알람음
    var effectiveSound: SoundSetting { sound ?? .preferences }
    /// 기상 습관 제안을 적용하거나 넘긴 시각(그 뒤 기록만 다시 모은다)
    var habitHandledAt: Date? = nil
    var lastError: String?

    var repeatLabel: String { Schedules.shared.repeatLabel(repeatMask: Int32(repeatMask)) }

    var arrivalText: String { String(format: "%02d:%02d", hour, minute) }

    /// "평일 반복", "화, 목 반복", "11월 27일 한 번"(안드로이드 repeatLabel)
    var repeatText: String { Schedules.shared.repeatHeader(repeatMask: Int32(repeatMask), targetMillis: plan.map { KotlinLong(value: $0.targetArrival.millis) }) }
}

extension Date {
    init(millis: Int64) { self.init(timeIntervalSince1970: TimeInterval(millis) / 1000) }
    var millis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
    /// shared(Kotlin) 의 시각
    var kotlinInstant: KotlinInstant { KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: millis) }
}
