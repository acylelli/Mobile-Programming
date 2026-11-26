import Foundation
import Shared

/// 출발지·도착지
struct Place: Codable, Hashable {
    var name: String
    var address: String
    var latitude: Double
    var longitude: Double

    init(name: String, address: String, latitude: Double, longitude: Double) {
        self.name = name
        self.address = address
        self.latitude = latitude
        self.longitude = longitude
    }

    init(_ place: TripPlace) {
        self.init(name: place.name, address: place.address, latitude: place.latitude, longitude: place.longitude)
    }

    var shared: TripPlace { TripPlace(name: name, address: address, latitude: latitude, longitude: longitude) }
}

struct Segment: Codable, Hashable {
    var mode: String
    var lineName: String?
    var from: String
    var to: String
    var minutes: Int
    var arrivalMessage: String?
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
            Segment(mode: $0.mode, lineName: $0.lineName, from: $0.from, to: $0.to, minutes: Int($0.minutes), arrivalMessage: $0.arrivalMessage)
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
                TripSegment(mode: $0.mode, lineName: $0.lineName, from: $0.from, to: $0.to, minutes: Int32($0.minutes), arrivalMessage: $0.arrivalMessage)
            },
            weather: weather.map {
                TripWeather(precipitation: $0.precipitation, probability: Int32($0.probability), extraMinutes: Int32($0.extraMinutes), umbrella: $0.umbrella)
            }
        )
    }

    func routeLine(origin: String, destination: String) -> RouteLine {
        RouteLineBuilder.shared.build(plan: shared, originName: origin, destinationName: destination)
    }

    /// "교통 정상", "지연 5분", "예상 경로"
    var status: (text: String, icon: String, delayed: Bool) {
        if usedFallback { return ("예상 경로", "clock", false) }
        if delayMinutes > 0 { return ("지연 \(delayMinutes)분", "exclamationmark.triangle.fill", true) }
        return (isRealtime ? "교통 정상" : "최근 교통 정보", "checkmark", false)
    }

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
    var enabled: Bool = true
    var plan: SavedPlan?
    var lastError: String?

    var repeatLabel: String { Schedules.shared.repeatLabel(repeatMask: Int32(repeatMask)) }

    var arrivalText: String { String(format: "%02d:%02d", hour, minute) }

    /// "평일 반복", "한 번"
    var repeatText: String { repeatMask == 0 ? "한 번" : "\(repeatLabel) 반복" }
}

extension Date {
    init(millis: Int64) { self.init(timeIntervalSince1970: TimeInterval(millis) / 1000) }
    var millis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
}
