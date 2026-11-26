import SwiftUI

/// 알람 탭(안드로이드 UI-D-001): 아이폰 시계식 큰 기상 시각, 아래 반복·도착과 교통 한 줄, 오른쪽 스위치
struct AlarmListView: View {
    @EnvironmentObject private var store: AlarmStore
    let onCreate: () -> Void
    let onOpen: (UUID) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "알람") {
                Button(action: onCreate) {
                    Image(systemName: "plus").font(.system(size: 22.dp, weight: .semibold)).foregroundStyle(AppColor.onButton)
                        .frame(width: 44.dp, height: 44.dp).background(Circle().fill(AppColor.button))
                }
                .accessibilityLabel("새 일정 만들기")
            }
            if store.alarms.isEmpty {
                Spacer().frame(height: Space.xl)
                EmptyStateView(title: "등록된 알람이 없어요", message: "도착지와 도착 시각만 정하면 기상 알람을 만들어드려요.",
                               actionTitle: "새 일정 만들기", action: onCreate)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(store.alarms) { alarm in
                            AlarmRow(alarm: alarm) { onOpen(alarm.id) }
                        }
                    }
                    .padding(.top, 8.dp).padding(.bottom, Space.lg)
                }
                .refreshable { await store.refreshAll() }
            }
        }
        .background(AppColor.background)
    }
}

struct AlarmRow: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: Alarm
    let onOpen: () -> Void

    var body: some View {
        let timeColor = alarm.enabled ? AppColor.textPrimary : AppColor.textTertiary
        VStack(spacing: 0) {
            HStack(spacing: 12.dp) {
                Button(action: onOpen) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(alignment: .lastTextBaseline, spacing: 4.dp) {
                            if let plan = alarm.plan {
                                let parts = TimeText.split(plan.wakeUp)
                                Text(parts.meridiem).font(AppFont.listMeridiem)
                                Text(parts.time).font(AppFont.listTime).monospacedDigit()
                            } else {
                                Text("--:--").font(AppFont.listTime)
                            }
                        }
                        .foregroundStyle(timeColor)
                        Text("\(alarm.repeatText) · \(alarm.destination.name) \(alarm.arrivalText) 도착")
                            .font(AppFont.bodyMedium).foregroundStyle(alarm.enabled ? AppColor.textSecondary : AppColor.textTertiary).lineLimit(1)
                        Spacer().frame(height: 4.dp)
                        TrafficLine(alarm: alarm)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                AppSwitch(isOn: Binding(get: { alarm.enabled }, set: { value in Task { await store.setEnabled(alarm.id, value) } }))
                    .accessibilityLabel("알람 켜기/끄기")
            }
            .padding(.horizontal, Space.screen).padding(.vertical, 14.dp)
            Rectangle().fill(AppColor.outline).frame(height: 1.dp).padding(.leading, Space.screen)
        }
    }
}

/// "● 교통 정상 · 07:59 출발 · 9분 전 갱신"(안드로이드 TrafficLine)
struct TrafficLine: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: Alarm

    var body: some View {
        let (color, text) = content
        HStack(spacing: 6.dp) {
            Circle().fill(color).frame(width: 7.dp, height: 7.dp)
            Text(text).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(1)
        }
    }

    private var content: (Color, String) {
        if store.refreshing.contains(alarm.id) { return (AppColor.textTertiary, "확인 중…") }
        if !alarm.enabled { return (AppColor.textTertiary, "알람 꺼짐") }
        guard let plan = alarm.plan else { return (AppColor.textTertiary, alarm.lastError ?? "계산 전") }
        let status = plan.status
        return (status.delayed ? AppColor.danger : AppColor.accent,
                "\(status.text) · \(TimeText.hm(plan.departure)) 출발 · \(UpdatedText.ago(plan.calculatedAt))")
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

    /// "11월 27일"
    static func monthDay(_ date: Date) -> String { formatter("M월 d일").string(from: date) }

    /// "11월 26일 목요일"
    static func fullDay(_ date: Date) -> String { formatter("M월 d일 EEEE").string(from: date) }

    /// "11월 27일 (금)"
    static func day(_ date: Date) -> String { formatter("M월 d일 (E)").string(from: date) }

    /// "11/29 (일) 09:20"(캘린더 다가오는 일정)
    static func dateTimeShort(_ date: Date) -> String { formatter("M/d (E) HH:mm").string(from: date) }

    /// "11월 26일 08:54"
    static func dateTime(_ date: Date) -> String { formatter("M월 d일 HH:mm").string(from: date) }

    /// 홈 제목(안드로이드 dayPartTitle): "내일 아침", "오늘 저녁", "금요일 아침"
    static func dayPartTitle(_ date: Date) -> String {
        let calendar = Calendar.current
        let hour = calendar.component(.hour, from: date)
        let part: String
        switch hour {
        case 4...11: part = "아침"
        case 12...17: part = "오후"
        case 18...21: part = "저녁"
        default: part = "밤"
        }
        let day: String
        let today = calendar.startOfDay(for: Date())
        let target = calendar.startOfDay(for: date)
        let days = calendar.dateComponents([.day], from: today, to: target).day ?? 0
        switch days {
        case 0: day = "오늘"
        case 1: day = "내일"
        case 2..<7: day = formatter("EEEE").string(from: date)
        default: day = monthDay(date)
        }
        return "\(day) \(part)"
    }
}

enum WeatherText {
    /// 안드로이드 weather_rain 등: 비, 소나기, 눈, 비·눈, 비 소식
    static func kind(_ precipitation: String) -> String {
        switch precipitation {
        case "RAIN": return "비"
        case "SHOWER": return "소나기"
        case "SNOW": return "눈"
        case "RAIN_SNOW": return "비·눈"
        default: return "비 소식"
        }
    }
}
