import Foundation
import WidgetKit

/// 홈 화면 위젯에 다음 알람 요약을 넘긴다(App Group 공유 저장소). 안드로이드 WidgetUpdater 와 같은 역할
enum WidgetBridge {
    private static let group = "group.com.example.routealarm.ios"

    struct Snapshot: Codable {
        var label: String
        var wakeUp: String
        var summary: String
        var traffic: String
        var theme: String
    }

    /// 켜져 있고 아직 울리지 않은 일정 중 기상 알람이 가장 이른 것(안드로이드 NextAlarm 과 같은 규칙)
    @MainActor
    static func update(_ alarms: [Alarm]) {
        guard let defaults = UserDefaults(suiteName: group) else { return }
        let now = Date()
        let next = alarms.filter { $0.enabled && ($0.plan?.departure ?? .distantPast) > now }
            .min { $0.plan!.wakeUp < $1.plan!.wakeUp }
        let theme = UserDefaults.standard.string(forKey: Prefs.widgetTheme) ?? ColorTheme.standard.rawValue
        defaults.set(theme, forKey: "widget.theme")
        if let next, let plan = next.plan {
            let snapshot = Snapshot(
                label: "\(TimeText.day(plan.targetArrival)) · \(next.destination.name)",
                wakeUp: TimeText.hm(plan.wakeUp),
                summary: "\(next.destination.name) \(next.arrivalText) 도착 | \(TimeText.hm(plan.departure)) 출발",
                traffic: plan.status.text,
                theme: theme
            )
            defaults.set(try? JSONEncoder().encode(snapshot), forKey: "widget.next")
        } else {
            defaults.removeObject(forKey: "widget.next")
        }
        WidgetCenter.shared.reloadAllTimelines()
    }
}
