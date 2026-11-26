import SwiftUI

enum AppTab: Hashable { case home, alarms, settings }

/// 홈 · 알람 · 설정 아래 탭(안드로이드 BottomBar 와 같은 구성)
struct RootView: View {
    @State private var tab: AppTab = .home
    @State private var creating: Alarm?

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch tab {
                case .home: HomeView(onCreate: startCreate)
                case .alarms: AlarmListView(onCreate: startCreate, onEdit: { creating = $0 })
                case .settings: SettingsView()
                }
            }
            .frame(maxHeight: .infinity)
            BottomBar(tab: $tab)
        }
        .background(AppColor.background)
        .fullScreenCover(item: $creating) { CreateFlowView(original: $0) }
    }

    private func startCreate() { creating = Alarm.draft }
}

private struct BottomBar: View {
    @Binding var tab: AppTab

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(AppColor.divider).frame(height: 1)
            HStack {
                item(.home, "house", "홈")
                item(.alarms, "alarm", "알람")
                item(.settings, "sun.max", "설정")
            }
            .padding(.top, 8)
        }
        .background(AppColor.background)
    }

    private func item(_ value: AppTab, _ icon: String, _ label: String) -> some View {
        let selected = tab == value
        return Button { tab = value } label: {
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 21, weight: selected ? .semibold : .regular))
                Text(label).font(.app(12, selected ? .bold : .regular))
                Circle().fill(selected ? AppColor.accent : .clear).frame(width: 5, height: 5)
            }
            .foregroundStyle(selected ? AppColor.textPrimary : AppColor.textTertiary)
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

extension Alarm {
    /// 새 알람 초안(만들기 5단계에서 채운다)
    static var draft: Alarm {
        Alarm(
            title: "", origin: Place(name: "", address: "", latitude: 0, longitude: 0),
            destination: Place(name: "", address: "", latitude: 0, longitude: 0),
            hour: 9, minute: 0, repeatMask: 0b0011111, preparationMinutes: 30, bufferMinutes: 10,
            useWeather: true, skipHolidays: true
        )
    }
}
