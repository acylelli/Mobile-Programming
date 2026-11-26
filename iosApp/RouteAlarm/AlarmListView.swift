import SwiftUI

/// 알람 탭: 아이폰 시계식 큰 기상 시각 + 아래 교통 정보(안드로이드 알람 탭과 같은 구성)
struct AlarmListView: View {
    @EnvironmentObject private var store: AlarmStore
    let onCreate: () -> Void
    let onEdit: (Alarm) -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack {
                    Text("알람").font(AppFont.headline).foregroundStyle(AppColor.textPrimary)
                    Spacer()
                    Button(action: onCreate) {
                        Image(systemName: "plus").font(.system(size: 22, weight: .semibold))
                            .foregroundStyle(AppColor.onButton)
                            .frame(width: 52, height: 52)
                            .background(Circle().fill(AppColor.button))
                    }
                    .accessibilityLabel("알람 추가")
                }
                .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 10)
                Rectangle().fill(AppColor.divider).frame(height: 1).padding(.leading, 20)

                if store.alarms.isEmpty {
                    EmptyAlarms(onCreate: onCreate).padding(.top, 100)
                    Spacer()
                } else {
                    List {
                        ForEach(store.alarms) { alarm in
                            NavigationLink(value: alarm.id) { AlarmRow(alarm: alarm) }
                                .listRowBackground(AppColor.background)
                                .listRowInsets(EdgeInsets(top: 0, leading: 20, bottom: 0, trailing: 20))
                                .listRowSeparatorTint(AppColor.divider)
                                .swipeActions {
                                    Button("삭제", role: .destructive) { store.delete(alarm.id) }
                                    Button("편집") { onEdit(alarm) }.tint(.gray)
                                }
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .refreshable { await store.refreshAll() }
                }
            }
            .background(AppColor.background)
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: UUID.self) { AlarmDetailView(alarmId: $0, onEdit: onEdit) }
        }
    }
}

struct AlarmRow: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: Alarm

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                if let plan = alarm.plan {
                    let parts = TimeText.split(plan.wakeUp)
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(parts.meridiem).font(.app(26))
                        Text(parts.time).font(.app(52, .regular)).monospacedDigit()
                    }
                    .foregroundStyle(alarm.enabled ? AppColor.textPrimary : AppColor.textTertiary)
                } else {
                    Text("계산 전").font(.app(34)).foregroundStyle(AppColor.textTertiary)
                }
                Text("\(alarm.repeatText) · \(alarm.destination.name) \(alarm.arrivalText) 도착")
                    .font(AppFont.body).foregroundStyle(AppColor.textSecondary).lineLimit(1)
                TrafficLine(alarm: alarm)
            }
            .padding(.vertical, 14)
            Spacer()
            Toggle("", isOn: Binding(get: { alarm.enabled }, set: { value in Task { await store.setEnabled(alarm.id, value) } }))
                .labelsHidden()
                .tint(AppColor.accent)
        }
    }
}

/// "● 교통 정상 · 07:59 출발 · 9분 전 갱신"
struct TrafficLine: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: Alarm

    var body: some View {
        HStack(spacing: 6) {
            if store.refreshing.contains(alarm.id) {
                ProgressView().controlSize(.mini)
                Text("교통 정보 확인 중")
            } else if let plan = alarm.plan {
                Circle().fill(plan.status.delayed ? AppColor.danger : AppColor.accent).frame(width: 6, height: 6)
                Text("\(plan.status.text) · \(TimeText.hm(plan.departure)) 출발 · \(UpdatedText.ago(plan.calculatedAt))")
                if let weather = plan.weather, weather.extraMinutes > 0 || weather.umbrella {
                    Image(systemName: WeatherText.symbol(weather.precipitation)).foregroundStyle(Color(hex: 0x2563EB))
                }
            } else if let error = alarm.lastError {
                Circle().fill(AppColor.danger).frame(width: 6, height: 6)
                Text(error)
            }
        }
        .font(AppFont.caption)
        .foregroundStyle(AppColor.textSecondary)
        .lineLimit(1)
    }
}

enum TimeText {
    private static func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = format
        return formatter
    }

    /// 오전/오후와 "7:20"
    static func split(_ date: Date) -> (meridiem: String, time: String) {
        (formatter("a").string(from: date), formatter("h:mm").string(from: date))
    }

    /// "07:59"
    static func hm(_ date: Date) -> String { formatter("HH:mm").string(from: date) }

    static func short(_ date: Date) -> String { AlarmNotifier.time(date) }

    /// "11월 27일 (금)"
    static func day(_ date: Date) -> String { formatter("M월 d일 (E)").string(from: date) }

    /// "오늘 아침", "내일 아침", "11월 30일 아침"
    static func relativeMorning(_ date: Date) -> String {
        let calendar = Calendar.current
        let part = calendar.component(.hour, from: date) < 12 ? "아침" : (calendar.component(.hour, from: date) < 18 ? "오후" : "저녁")
        if calendar.isDateInToday(date) { return "오늘 \(part)" }
        if calendar.isDateInTomorrow(date) { return "내일 \(part)" }
        return "\(formatter("M월 d일").string(from: date)) \(part)"
    }
}

enum WeatherText {
    static func symbol(_ precipitation: String) -> String {
        switch precipitation {
        case "SNOW", "RAIN_SNOW": return "cloud.snow.fill"
        case "RAIN", "SHOWER": return "cloud.rain.fill"
        default: return "umbrella.fill"
        }
    }

    static func label(_ weather: WeatherNote) -> String {
        let kind: String
        switch weather.precipitation {
        case "RAIN": kind = "비 예보"
        case "SHOWER": kind = "소나기 예보"
        case "SNOW": kind = "눈 예보"
        case "RAIN_SNOW": kind = "진눈깨비 예보"
        default: kind = "강수확률 \(weather.probability)%"
        }
        return weather.extraMinutes > 0 ? "\(kind) · 여유 +\(weather.extraMinutes)분" : "\(kind) · 우산 챙기세요"
    }
}
