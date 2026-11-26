import Shared
import SwiftUI

enum AppTab: Hashable { case home, alarms, settings }

/// 홈 · 알람 · 설정 아래 탭(안드로이드 RouteAlarmBottomBar 와 같은 모양). 상세·테마 화면은 탭 위로 쌓인다.
struct RootView: View {
    @EnvironmentObject private var store: AlarmStore
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @State private var tab: AppTab = .home
    @State private var showingGroupPass = false
    @State private var limitMessage = false
    @State private var path: [UUID] = []
    @State private var creating: Alarm?
    @State private var snackbar: String?

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                Group {
                    switch tab {
                    case .home: HomeView(onCreate: startCreate, onOpen: open)
                    case .alarms: AlarmListView(onCreate: addFromList, onOpen: open)
                    case .settings: SettingsView(onRingDemo: { store.ringing = store.nextAlarm?.id ?? store.alarms.first?.id })
                    }
                }
                .frame(maxHeight: .infinity)
                BottomBar(tab: $tab)
            }
            .background(AppColor.background)
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(isPresented: $showingGroupPass) { GroupPassView() }
            .navigationDestination(for: UUID.self) { id in
                AlarmDetailView(alarmId: id, onEdit: { creating = $0 }, onDeleted: {
                    path.removeAll()
                    snackbar = "알람을 삭제했어요."
                })
            }
        }
        .overlay(alignment: .bottom) {
            if let snackbar {
                Snackbar(text: snackbar, actionTitle: "되돌리기", action: { Task { await store.restoreDeleted() } }) { self.snackbar = nil }
                    .padding(.bottom, 72.dp)
            } else if limitMessage {
                Snackbar(text: "무료 플랜은 일정을 \(PlanLimits.shared.FREE_SCHEDULE_LIMIT)개까지 만들 수 있어요.", actionTitle: "PRO 보기",
                         action: { subscriptions.paywallVisible = true }) { limitMessage = false }
                    .padding(.bottom, 72.dp)
            } else if let message = subscriptions.message {
                Snackbar(text: message) { subscriptions.message = nil }.padding(.bottom, 72.dp)
            }
        }
        .sheet(isPresented: $subscriptions.paywallVisible) {
            PaywallSheet {
                subscriptions.paywallVisible = false
                showingGroupPass = true
            }
            .environmentObject(subscriptions)
        }
        .sheet(item: Binding(get: { subscriptions.checkoutURL.map(CheckoutPage.init) }, set: { subscriptions.checkoutURL = $0?.url }),
               onDismiss: { Task { await subscriptions.resumePending() } }) { page in
            CheckoutSafari(url: page.url).ignoresSafeArea()
        }
        .fullScreenCover(item: $creating) { CreateFlowView(original: $0) }
        .fullScreenCover(item: Binding(get: { store.ringing.map(RingingID.init) }, set: { store.ringing = $0?.id })) {
            RingingView(alarmId: $0.id)
        }
    }

    private func startCreate() { creating = Alarm.draft }

    /// 알람 탭의 새 일정(+): 무료 플랜 한도에 차 있으면 만들기 대신 PRO 페이월(하루 한 번) 또는 안내
    private func addFromList() {
        switch subscriptions.addDecision(scheduleCount: store.alarms.count) {
        case .create: startCreate()
        case .paywall: subscriptions.paywallVisible = true
        case .limitMessage: limitMessage = true
        }
    }
    private func open(_ id: UUID) { path = [id] }
}

private struct RingingID: Identifiable { let id: UUID }

private struct CheckoutPage: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

private struct BottomBar: View {
    @Binding var tab: AppTab

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(AppColor.divider).frame(height: 1.dp)
            HStack(spacing: 0) {
                item(.home, "house", "홈")
                item(.alarms, "alarm", "알람")
                item(.settings, "sun.max", "설정")
            }
            .frame(height: 72.dp)
        }
        .background(AppColor.background)
    }

    private func item(_ value: AppTab, _ icon: String, _ label: String) -> some View {
        let selected = tab == value
        let tint = selected ? AppColor.textPrimary : AppColor.textTertiary
        return Button { tab = value } label: {
            VStack(spacing: 0) {
                Spacer().frame(height: 12.dp)
                Image(systemName: icon).font(.system(size: 20.dp)).frame(width: 24.dp, height: 24.dp)
                Spacer().frame(height: 4.dp)
                Text(label).font(.app(12, selected ? .bold : .medium))
                Spacer().frame(height: 5.dp)
                Circle().fill(selected ? AppColor.accent : .clear).frame(width: 5.dp, height: 5.dp)
                Spacer(minLength: 0)
            }
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

extension Alarm {
    /// 새 알람 초안(만들기 5단계에서 채운다). 준비·여유는 설정의 기본값
    static var draft: Alarm {
        Alarm(
            title: "", origin: Place(name: "", address: "", latitude: 0, longitude: 0),
            destination: Place(name: "", address: "", latitude: 0, longitude: 0),
            hour: 9, minute: 0, repeatMask: 0b0011111, preparationMinutes: Prefs.preparationMinutes, bufferMinutes: Prefs.bufferMinutes,
            useWeather: Prefs.weatherEnabled, skipHolidays: true
        )
    }
}
