import Foundation
import UserNotifications

/// 기상 알람과 출발 알림을 iOS 로컬 알림으로 예약한다.
/// 기상 알람은 1분 간격으로 3번 더 울려 놓쳐도 다시 깨운다.
enum AlarmNotifier {
    private static let center = UNUserNotificationCenter.current()
    private static let repeats = 3

    static func requestPermission() async {
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    static func cancel(_ id: UUID) {
        let ids = identifiers(id)
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }

    static func schedule(_ alarm: Alarm, _ plan: SavedPlan) async {
        cancel(alarm.id)
        let now = Date()
        let line = plan.mainLine.map { " · \($0)" } ?? ""

        for index in 0...repeats {
            let date = plan.wakeUp.addingTimeInterval(TimeInterval(index * 60))
            guard date > now else { continue }
            let content = UNMutableNotificationContent()
            content.title = index == 0 ? "일어날 시간이에요" : "다시 울림"
            content.body = "\(alarm.title) · \(time(plan.departure)) 출발\(line)"
            content.sound = .default
            content.interruptionLevel = .timeSensitive
            await add("\(alarm.id.uuidString).wake.\(index)", content, date)
        }

        if plan.departure > now {
            let content = UNMutableNotificationContent()
            content.title = "지금 출발하세요"
            var body = "\(alarm.destination.name)까지 \(plan.travelMinutes)분\(line)"
            if plan.weather?.umbrella == true { body += " · 우산 챙기세요" }
            content.body = body
            content.sound = .default
            await add("\(alarm.id.uuidString).depart", content, plan.departure)
        }
    }

    private static func add(_ id: String, _ content: UNMutableNotificationContent, _ date: Date) async {
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        let request = UNNotificationRequest(identifier: id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false))
        try? await center.add(request)
    }

    private static func identifiers(_ id: UUID) -> [String] {
        (0...repeats).map { "\(id.uuidString).wake.\($0)" } + ["\(id.uuidString).depart"]
    }

    static func time(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "a h:mm"
        return formatter.string(from: date)
    }
}
