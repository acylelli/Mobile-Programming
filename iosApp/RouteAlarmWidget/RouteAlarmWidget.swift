import SwiftUI
import WidgetKit

/// 앱이 App Group 에 써 두는 다음 알람 요약(앱의 WidgetSnapshot 과 같은 모양)
struct WidgetSnapshot: Codable {
    var label: String
    var wakeUp: String
    var summary: String
    var traffic: String
    var theme: String
    static let key = "widget.next"
    static let group = "group.com.example.routealarm.ios"

    static func load() -> WidgetSnapshot? {
        guard let data = UserDefaults(suiteName: group)?.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    static func theme() -> String { UserDefaults(suiteName: group)?.string(forKey: "widget.theme") ?? "DEFAULT" }
}

struct NextAlarmEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    let theme: String
}

struct NextAlarmProvider: TimelineProvider {
    func placeholder(in context: Context) -> NextAlarmEntry {
        NextAlarmEntry(date: .now, snapshot: WidgetSnapshot(label: "11월 27일 (금) · 한성대학교", wakeUp: "07:29",
                                                            summary: "한성대학교 09:00 도착 | 07:59 출발", traffic: "교통 정상", theme: "DEFAULT"),
                       theme: "DEFAULT")
    }

    func getSnapshot(in context: Context, completion: @escaping (NextAlarmEntry) -> Void) {
        completion(WidgetSnapshot.load().map { NextAlarmEntry(date: .now, snapshot: $0, theme: WidgetSnapshot.theme()) } ?? placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NextAlarmEntry>) -> Void) {
        let entry = NextAlarmEntry(date: .now, snapshot: WidgetSnapshot.load(), theme: WidgetSnapshot.theme())
        // 앱이 계산할 때마다 다시 그리게 하고, 그 사이에는 30분마다 새로 읽는다.
        completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(1800))))
    }
}

/// 안드로이드 NextAlarmWidget 과 같은 모양: 모서리 22, 가로 18·세로 14 여백, 날짜·목적지 13, 기상 시각 34, 요약 14, 교통 13
struct NextAlarmWidgetView: View {
    let entry: NextAlarmEntry

    var body: some View {
        let style = WidgetStyle(entry.theme)
        VStack(alignment: .leading, spacing: 0) {
            if let snapshot = entry.snapshot {
                Text(snapshot.label).font(.system(size: 13)).foregroundStyle(style.secondary).lineLimit(1)
                Text(snapshot.wakeUp).font(.system(size: 34, weight: .regular)).monospacedDigit().foregroundStyle(style.primary)
                Text(snapshot.summary).font(.system(size: 14)).foregroundStyle(style.primary).lineLimit(1)
                Spacer().frame(height: 2)
                Text(snapshot.traffic).font(.system(size: 13, weight: .medium)).foregroundStyle(style.accent)
            } else {
                Text("다음 알람").font(.system(size: 13)).foregroundStyle(style.secondary)
                Spacer().frame(height: 6)
                Text("예정된 알람이 없어요").font(.system(size: 16, weight: .medium)).foregroundStyle(style.primary)
                Text("눌러서 알람 만들기").font(.system(size: 13)).foregroundStyle(style.accent)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .containerBackground(for: .widget) { style.background }
    }
}

/// 위젯 배경 3종(안드로이드 widget_bg_default · dawn · night 와 같은 그라데이션·글자색)
struct WidgetStyle {
    let background: LinearGradient
    let primary: Color
    let secondary: Color
    let accent: Color

    init(_ theme: String) {
        func c(_ hex: UInt32) -> Color {
            Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
        }
        switch theme {
        case "DAWN":
            background = LinearGradient(colors: [c(0x1B2140), c(0x3D3566), c(0x8E5E72)], startPoint: .topLeading, endPoint: .bottomTrailing)
            primary = c(0xF4EFE8); secondary = c(0xC9C2D6); accent = c(0xF2B98B)
        case "NIGHT":
            background = LinearGradient(colors: [c(0x0B0F1C), c(0x1F2942)], startPoint: .topLeading, endPoint: .bottomTrailing)
            primary = c(0xF2F3F7); secondary = c(0xA0A6BA); accent = c(0xC8F560)
        default:
            background = LinearGradient(colors: [c(0xFF8A4C), c(0xF0491A)], startPoint: .topLeading, endPoint: .bottomTrailing)
            primary = .white; secondary = c(0xFFE2D4); accent = c(0xFFF2B8)
        }
    }
}

struct NextAlarmWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NextAlarm", provider: NextAlarmProvider()) { entry in
            NextAlarmWidgetView(entry: entry)
        }
        .configurationDisplayName("다음 알람")
        .description("다음 기상 알람 시각과 출발·도착, 교통 상태를 보여 줘요.")
        .supportedFamilies([.systemMedium, .systemSmall])
    }
}

@main
struct RouteAlarmWidgets: WidgetBundle {
    var body: some Widget { NextAlarmWidget() }
}
