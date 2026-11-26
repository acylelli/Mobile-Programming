import AVFoundation
import SwiftUI
import UserNotifications

/// 알람음(안드로이드 AlarmSound 와 같은 선택지). iOS 는 앱이 휴대폰 벨소리를 쓸 수 없어 "휴대폰 벨소리" 대신 기기 알림음을 쓴다.
enum AlarmSoundKind: String, Codable, CaseIterable, Identifiable {
    case standard = "DEFAULT_ALARM", gentle = "GENTLE", device = "CUSTOM", silent = "SILENT"
    var id: String { rawValue }

    var label: String {
        switch self {
        case .standard: return "기본 알람음"
        case .gentle: return "부드럽게"
        case .device: return "기기 알림음"
        case .silent: return "무음"
        }
    }

    var file: String? {
        switch self {
        case .standard: return "alarm_default"
        case .gentle: return "alarm_gentle"
        default: return nil
        }
    }

    /// 앱이 꺼져 있을 때 울리는 알림 소리(볼륨은 기기 설정을 따른다)
    var notificationSound: UNNotificationSound? {
        switch self {
        case .standard, .gentle: return UNNotificationSound(named: UNNotificationSoundName("\(file!).caf"))
        case .device: return .default
        case .silent: return nil
        }
    }
}

/// 소리·볼륨·점점 크게 한 묶음(일정 전용 알람음에도 쓴다)
struct SoundSetting: Codable, Hashable {
    var kind: AlarmSoundKind
    var volume: Int
    var gradual: Bool

    static let volumeRange = 10...100

    /// 설정 화면 값(안드로이드 soundValue): "기본 알람음 · 80%", 무음이면 이름만
    var summary: String { kind == .silent ? kind.label : "\(kind.label) · \(volume)%" }

    static var preferences: SoundSetting {
        SoundSetting(
            kind: AlarmSoundKind(rawValue: UserDefaults.standard.string(forKey: "alarmSound") ?? "") ?? .standard,
            volume: Prefs.int("alarmVolumePercent", 80),
            gradual: Prefs.bool("gradualVolume", false)
        )
    }

    func saveAsPreferences() {
        UserDefaults.standard.set(kind.rawValue, forKey: "alarmSound")
        UserDefaults.standard.set(volume, forKey: "alarmVolumePercent")
        UserDefaults.standard.set(gradual, forKey: "gradualVolume")
    }
}

/// 기상 알람 화면에서 소리를 낸다. 정한 크기로(점점 크게면 10% → 30초에 걸쳐 정한 크기까지), 진동을 켰으면 함께
@MainActor
final class AlarmSoundPlayer {
    static let shared = AlarmSoundPlayer()
    private var player: AVAudioPlayer?
    private var timer: Timer?
    private var startedAt = Date()

    func start(_ setting: SoundSetting, vibrate: Bool, loop: Bool = true) {
        stop()
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [])
        try? AVAudioSession.sharedInstance().setActive(true)
        startedAt = Date()
        let target = Float(setting.volume) / 100
        if let file = setting.kind.file, let url = Bundle.main.url(forResource: file, withExtension: "caf") {
            player = try? AVAudioPlayer(contentsOf: url)
            player?.numberOfLoops = loop ? -1 : 0
            player?.volume = setting.gradual ? 0.1 : target
            player?.play()
        }
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if setting.gradual, let player = self.player {
                    let progress = min(1, Float(Date().timeIntervalSince(self.startedAt) / 30))
                    player.volume = 0.1 + (target - 0.1) * progress
                }
                if setting.kind == .device, Int(Date().timeIntervalSince(self.startedAt)) % 2 == 0 {
                    AudioServicesPlayAlertSound(SystemSoundID(1005))
                }
                if vibrate { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
            }
        }
        if setting.kind == .device { AudioServicesPlayAlertSound(SystemSoundID(1005)) }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        player?.stop()
        player = nil
    }
}

/// 알람음 시트(안드로이드 AlarmSoundSheet): 소리 고르기, 볼륨, 점점 크게, [미리 듣기] [확인]
struct AlarmSoundSheet: View {
    let title: String
    /// 일정 전용이면 "설정 따르기" 선택지를 보인다
    let followSettings: Bool?
    let onConfirm: (SoundSetting?) -> Void
    @State private var setting: SoundSetting
    @State private var follow: Bool
    @State private var previewing = false
    /// 내용 높이만큼만 시트를 올린다(안드로이드 바텀시트처럼)
    @State private var contentHeight: CGFloat = 520
    @Environment(\.dismiss) private var dismiss

    init(title: String = "알람음", initial: SoundSetting, followSettings: Bool? = nil, onConfirm: @escaping (SoundSetting?) -> Void) {
        self.title = title
        self.followSettings = followSettings
        self.onConfirm = onConfirm
        _setting = State(initialValue: initial)
        _follow = State(initialValue: followSettings ?? false)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(AppFont.headlineSmall).foregroundStyle(AppColor.textPrimary)
            Spacer().frame(height: 8.dp)
            if followSettings != nil {
                option(selected: follow, label: "설정 따르기", detail: SoundSetting.preferences.summary) { follow = true }
            }
            ForEach(AlarmSoundKind.allCases) { kind in
                option(selected: !follow && setting.kind == kind, label: kind.label, detail: nil) {
                    follow = false
                    setting.kind = kind
                }
            }
            if !follow && setting.kind != .silent {
                Spacer().frame(height: 12.dp)
                HStack {
                    Text("볼륨").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                    Spacer()
                    Text("\(setting.volume)%").font(AppFont.titleMedium).foregroundStyle(AppColor.accentText)
                }
                Spacer().frame(height: 10.dp)
                VolumeSlider(value: $setting.volume)
                Spacer().frame(height: 8.dp)
                Text("휴대폰 알람 볼륨과 관계없이 이 크기로 울려요. 알람이 끝나면 원래 볼륨으로 돌아가요.")
                    .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                if followSettings == nil {
                    Spacer().frame(height: 14.dp)
                    HStack(spacing: 12.dp) {
                        VStack(alignment: .leading, spacing: 0) {
                            Text("점점 크게 울리기").font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary)
                            Text("작게 시작해서 30초에 걸쳐 정한 크기까지 커져요.").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                        }
                        Spacer()
                        AppSwitch(isOn: $setting.gradual)
                    }
                    .padding(.vertical, 6.dp)
                }
            }
            Spacer().frame(height: 20.dp)
            HStack(spacing: 10.dp) {
                SecondaryButton(title: previewing ? "멈추기" : "미리 듣기") {
                    if previewing {
                        AlarmSoundPlayer.shared.stop()
                    } else {
                        AlarmSoundPlayer.shared.start(follow ? SoundSetting.preferences : setting, vibrate: false, loop: false)
                    }
                    previewing.toggle()
                }
                .frame(height: 60.dp)
                PrimaryButton(title: "확인") {
                    AlarmSoundPlayer.shared.stop()
                    onConfirm(follow ? nil : setting)
                    dismiss()
                }
            }
        }
        .padding(.horizontal, Space.screen).padding(.top, 28.dp).padding(.bottom, 16.dp)
        .fixedSize(horizontal: false, vertical: true)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { contentHeight = $0 }
        .frame(maxHeight: .infinity, alignment: .top)
        .presentationDetents([.height(contentHeight)])
        .presentationDragIndicator(.visible)
        .presentationBackground(AppColor.background)
        .onDisappear { AlarmSoundPlayer.shared.stop() }
    }

    private func option(selected: Bool, label: String, detail: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14.dp) {
                RadioDot(selected: selected)
                VStack(alignment: .leading, spacing: 0) {
                    Text(label).font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary)
                    if let detail { Text(detail).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(1) }
                }
                Spacer()
            }
            .padding(.horizontal, 4.dp).padding(.vertical, 12.dp)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// 볼륨 막대(안드로이드 VolumeSlider): 회색 바탕 안에 작은·큰 스피커와 강조색 막대, 44×28 손잡이
struct VolumeSlider: View {
    @Binding var value: Int

    var body: some View {
        HStack(spacing: 10.dp) {
            Image(systemName: "speaker.fill").font(.system(size: 16.dp)).foregroundStyle(AppColor.textSecondary)
            GeometryReader { geo in
                let range = CGFloat(SoundSetting.volumeRange.upperBound - SoundSetting.volumeRange.lowerBound)
                let fraction = CGFloat(value - SoundSetting.volumeRange.lowerBound) / range
                let knob = 44.dp
                let x = (geo.size.width - knob) * fraction
                ZStack(alignment: .leading) {
                    Capsule().fill(AppColor.dotted).frame(height: 8.dp)
                    Capsule().fill(AppColor.accent).frame(width: x + knob / 2, height: 8.dp)
                    Capsule().fill(Color.white).frame(width: knob, height: 28.dp).shadow(color: .black.opacity(0.18), radius: 3.dp, y: 1)
                        .offset(x: x)
                }
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { drag in
                    let f = min(max((drag.location.x - knob / 2) / (geo.size.width - knob), 0), 1)
                    value = SoundSetting.volumeRange.lowerBound + Int((f * range).rounded())
                })
            }
            .frame(height: 44.dp)
            Image(systemName: "speaker.wave.3.fill").font(.system(size: 16.dp)).foregroundStyle(AppColor.textSecondary)
        }
        .padding(.horizontal, 14.dp)
        .frame(height: 56.dp)
        .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
        .accessibilityElement()
        .accessibilityLabel("볼륨")
        .accessibilityValue("\(value)%")
        .accessibilityAdjustableAction { direction in
            value = min(max(value + (direction == .increment ? 10 : -10), SoundSetting.volumeRange.lowerBound), SoundSetting.volumeRange.upperBound)
        }
    }
}
