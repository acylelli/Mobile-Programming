import SwiftUI
import UserNotifications

@main
struct RouteAlarmApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var store = AlarmStore.shared
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage(ColorTheme.storageKey) private var themeRaw = ColorTheme.standard.rawValue
    @AppStorage("onboardingDone") private var onboardingDone = false

    init() { AppFonts.register() }

    var body: some Scene {
        WindowGroup {
            Group {
                if onboardingDone { RootView() } else { OnboardingView { onboardingDone = true } }
            }
            .environmentObject(store)
            .tint(AppColor.accent)
            .font(AppFont.bodyLarge.font)
            // 테마를 바꾸면 화면 전체를 새 색으로 다시 그린다. 나이트는 항상 어둡게.
            .id(themeRaw)
            .preferredColorScheme(ColorTheme(rawValue: themeRaw) == .night ? .dark : nil)
        }
        .onChange(of: scenePhase) { _, phase in
            // 앱을 열 때마다 교통 상황으로 다시 계산해 알람을 맞춘다.
            if phase == .active { Task { await store.refreshAll() } }
        }
    }
}

/// 앱이 켜져 있을 때도 알람 알림을 배너와 소리로 보여 준다.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// 앱을 보고 있을 때 기상 알람이 울리면 알림 대신 기상 알람 화면을 띄운다.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        if let id = Self.alarmId(notification), notification.request.identifier.contains(".wake.") || notification.request.identifier.hasSuffix(".snooze") {
            await MainActor.run { AlarmStore.shared.ringing = id }
            return []
        }
        return [.banner, .sound, .list]
    }

    /// 기상 알림을 누르면 기상 알람 화면(끄기·다시 울림)을 연다.
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let request = response.notification.request
        if let id = Self.alarmId(response.notification), request.identifier.contains(".wake.") || request.identifier.hasSuffix(".snooze") {
            await MainActor.run { AlarmStore.shared.ringing = id }
        }
    }

    private static func alarmId(_ notification: UNNotification) -> UUID? {
        (notification.request.content.userInfo["alarmId"] as? String).flatMap(UUID.init(uuidString:))
    }
}
