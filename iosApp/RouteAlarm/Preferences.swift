import Foundation

/// 사용자 설정(안드로이드 UserPreferences 와 같은 항목·기본값). UserDefaults 키 이름은 @AppStorage 에서 쓴다.
enum Prefs {
    static let defaultPreparation = "defaultPreparationMinutes"
    static let defaultBuffer = "defaultBufferMinutes"
    static let autoAdjust = "autoAdjustEnabled"
    static let trafficNotifications = "trafficNotificationsEnabled"
    static let weather = "weatherEnabled"
    static let vibration = "vibrationEnabled"
    static let snooze = "snoozeMinutes"
    static let widgetTheme = "widgetTheme"

    static let preparationRange = 5...120
    static let preparationPresets = [15, 30, 45, 60]
    static let bufferRange = 0...60
    static let bufferPresets = [5, 10, 20]
    static let snoozeOptions = [5, 10, 15]

    static func int(_ key: String, _ fallback: Int) -> Int {
        UserDefaults.standard.object(forKey: key) as? Int ?? fallback
    }

    static func bool(_ key: String, _ fallback: Bool = true) -> Bool {
        UserDefaults.standard.object(forKey: key) as? Bool ?? fallback
    }

    static var preparationMinutes: Int { int(defaultPreparation, 30) }
    static var bufferMinutes: Int { int(defaultBuffer, 10) }
    static var snoozeMinutes: Int { int(snooze, 5) }
    static var weatherEnabled: Bool { bool(weather) }
}
