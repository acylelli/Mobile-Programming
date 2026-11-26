import SwiftUI
import UserNotifications

@main
struct RouteAlarmApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var store = AlarmStore()
    @Environment(\.scenePhase) private var scenePhase

    init() { AppFonts.register() }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .tint(AppColor.accent)
                .font(.app(17))
                .task { await AlarmNotifier.requestPermission() }
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

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }
}
