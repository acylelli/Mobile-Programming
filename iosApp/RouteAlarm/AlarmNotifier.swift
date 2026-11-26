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
            content.title = index == 0 ? "\(alarm.title) 기상 알람" : "다시 울림"
            content.body = "\(alarm.title) · \(time(plan.departure)) 출발\(line)"
            content.sound = alarm.effectiveSound.kind.notificationSound
            content.userInfo = ["alarmId": alarm.id.uuidString]
            content.interruptionLevel = .timeSensitive
            await add("\(alarm.id.uuidString).wake.\(index)", content, date)
        }

        // 출발 알림: 출발 10분 전과 출발 시각(알림 방식이 "기상 알람만"이면 보내지 않는다)
        guard alarm.wakeOnly != true else { return }
        var umbrella = ""
        if plan.weather?.umbrella == true { umbrella = " · 우산 챙기세요" }
        let soon = plan.departure.addingTimeInterval(-600)
        if soon > now {
            let content = UNMutableNotificationContent()
            content.title = "10분 뒤 출발이에요"
            content.body = "\(TimeText.hm(plan.departure)) 출발 · \(alarm.destination.name)까지 \(plan.travelMinutes)분\(line)\(umbrella)"
            content.sound = .default
            await add("\(alarm.id.uuidString).depart.soon", content, soon)
        }
        if plan.departure > now {
            let content = UNMutableNotificationContent()
            content.title = "지금 출발하세요"
            content.body = "\(alarm.destination.name)까지 \(plan.travelMinutes)분\(line)\(umbrella)"
            content.sound = .default
            await add("\(alarm.id.uuidString).depart", content, plan.departure)
        }
    }

    /// 끄기를 누르면 같은 회차의 남은 반복 알림과 다시 울림을 지운다.
    static func cancelWakeRepeats(_ id: UUID) {
        let ids = (0...repeats).map { "\(id.uuidString).wake.\($0)" } + ["\(id.uuidString).snooze"]
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }

    /// N분 뒤 다시 울림
    static func scheduleSnooze(_ alarm: Alarm, after minutes: Int) async {
        cancelWakeRepeats(alarm.id)
        let content = UNMutableNotificationContent()
        content.title = "다시 울림"
        content.body = "\(alarm.title) · \(minutes)분 지났어요"
        content.sound = alarm.effectiveSound.kind.notificationSound
        content.userInfo = ["alarmId": alarm.id.uuidString]
        content.interruptionLevel = .timeSensitive
        await add("\(alarm.id.uuidString).snooze", content, Date().addingTimeInterval(TimeInterval(minutes * 60)))
    }

    private static func add(_ id: String, _ content: UNMutableNotificationContent, _ date: Date) async {
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        let request = UNNotificationRequest(identifier: id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: parts, repeats: false))
        try? await center.add(request)
    }

    private static func identifiers(_ id: UUID) -> [String] {
        (0...repeats).map { "\(id.uuidString).wake.\($0)" } + ["\(id.uuidString).depart", "\(id.uuidString).depart.soon", "\(id.uuidString).snooze"]
    }

    static func time(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "a h:mm"
        return formatter.string(from: date)
    }
}
