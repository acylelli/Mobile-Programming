import SwiftUI
import Shared
import UserNotifications

/// 설정(안드로이드 UI-F-001 과 같은 묶음·순서·문구)
struct SettingsView: View {
    @EnvironmentObject private var store: AlarmStore
    @AppStorage(Prefs.defaultPreparation) private var preparation = 30
    @AppStorage(Prefs.defaultBuffer) private var buffer = 10
    @AppStorage(Prefs.autoAdjust) private var autoAdjust = true
    @AppStorage(Prefs.trafficNotifications) private var trafficNotifications = true
    @AppStorage(Prefs.weather) private var weather = true
    @AppStorage(Prefs.vibration) private var vibration = true
    @AppStorage(Prefs.snooze) private var snooze = 5
    @AppStorage(Prefs.widgetTheme) private var widgetTheme = ColorTheme.standard.rawValue
    @AppStorage(ColorTheme.storageKey) private var theme = ColorTheme.standard.rawValue

    @State private var sheet: Sheet?
    @State private var notifications: UNAuthorizationStatus = .notDetermined
    @State private var refreshing = false
    @State private var toast: String?
    @State private var showingSound = false
    @State private var soundSummary = SoundSetting.preferences.summary
    let onRingDemo: () -> Void

    enum Sheet: String, Identifiable { case preparation, buffer, snooze, widget; var id: String { rawValue } }

    var body: some View {
        Group {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ScreenHeader(title: "설정")
                    VStack(alignment: .leading, spacing: 22.dp) {
                        group("알람 기본값") {
                            row("clock", "기본 준비 시간", value: "\(preparation)분") { sheet = .preparation }
                            divider
                            row("clock", "기본 여유 시간", value: "\(buffer)분") { sheet = .buffer }
                            divider
                            toggleRow("arrow.triangle.2.circlepath", "실시간 자동 조정", body: "교통이 바뀌면 알람을 자동으로 옮겨요", isOn: $autoAdjust)
                        }
                        group("연동") {
                            link("book.fill", "학교 시간표 연동") { PendingFeatureView(title: "학교 시간표") }
                            divider
                            link("calendar", "캘린더 연동") { PendingFeatureView(title: "캘린더 연동") }
                        }
                        group("기록") {
                            link("chart.bar.fill", "기록·통계", body: "최근 30일 제시간 기상률과 알람 조정 기록") { StatsView() }
                        }
                        group("화면") {
                            link("paintpalette", "테마", value: (ColorTheme(rawValue: theme) ?? .standard).longLabel) { ThemeView() }
                            divider
                            row("square.grid.2x2", "위젯 배경", value: (ColorTheme(rawValue: widgetTheme) ?? .standard).longLabel) { sheet = .widget }
                        }
                        group("구독") {
                            link("ticket", "단체 이용권 코드 입력", value: "학교, 기업") { PendingFeatureView(title: "단체 이용권") }
                            divider
                            link("creditcard", "구독 관리", value: "무료") { PendingFeatureView(title: "구독 관리") }
                        }
                        group("알람 소리와 알림") {
                            toggleRow("car", "교통 상황 알림", isOn: $trafficNotifications)
                            divider
                            toggleRow("umbrella", "날씨 반영", body: "출발 시각에 비 오면 5분, 눈 오면 10분 여유를 더하고 우산을 챙기라고 알려 줘요", isOn: $weather)
                            divider
                            toggleRow("iphone.radiowaves.left.and.right", "진동", isOn: $vibration)
                            divider
                            row("speaker.wave.2", "알람음", value: soundSummary) { showingSound = true }
                            divider
                            row("zzz", "다시 울림 간격", value: "\(snooze)분") { sheet = .snooze }
                        }
                        group("권한") {
                            row("bell", "알림", value: permissionText, valueColor: notifications == .denied ? AppColor.danger : nil) { openSettings() }
                            divider
                            row("alarm", "정확한 알람", value: notifications == .denied ? "권한 필요" : "허용됨",
                                valueColor: notifications == .denied ? AppColor.danger : nil) { openSettings() }
                            divider
                            row("location", "위치", value: "허용 안 됨") { openSettings() }
                        }
                        group("테스트 도구") {
                            row("arrow.triangle.2.circlepath", refreshing ? "교통 정보 확인 중" : "지금 교통 정보 다시 확인") {
                                guard !refreshing else { return }
                                Task {
                                    refreshing = true
                                    await store.refreshAll()
                                    refreshing = false
                                    toast = "교통 정보를 다시 확인했어요."
                                }
                            }
                            divider
                            row("alarm", "기상 알람 미리 울려보기") {
                                if store.alarms.isEmpty { toast = "먼저 일정을 만들어 주세요." } else { onRingDemo() }
                            }
                        }
                        VStack(alignment: .leading, spacing: 4.dp) {
                            SectionLabel(text: "측정 지표").padding(.bottom, 6.dp)
                            metric("API 호출 \(store.metrics.apiCalls)회 (실패 \(store.metrics.apiFailures)회)")
                            metric("재계산 \(store.metrics.recalculations)회")
                            metric("계산 엔진: Kotlin Multiplatform 공용 모듈(shared)")
                        }
                        Text("버전 \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-")")
                            .font(AppFont.bodySmall).foregroundStyle(AppColor.textTertiary).frame(maxWidth: .infinity)
                    }
                    .padding(.horizontal, Space.screen)
                    .padding(.top, 16.dp)
                    .padding(.bottom, Space.lg)
                }
            }
            .background(AppColor.background)
            .toolbar(.hidden, for: .navigationBar)
            .overlay(alignment: .bottom) { if let toast { Snackbar(text: toast) { self.toast = nil } } }
        }
        .task { notifications = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus }
        .sheet(isPresented: $showingSound) {
            AlarmSoundSheet(initial: .preferences) { setting in
                setting?.saveAsPreferences()
                soundSummary = SoundSetting.preferences.summary
            }
        }
        .sheet(item: $sheet) { sheet in
            switch sheet {
            case .preparation:
                MinutePickerSheet(title: "기본 준비 시간", message: "새 일정을 만들 때 처음 채워지는 준비 시간이에요.", initial: preparation,
                                  range: Prefs.preparationRange, presets: Prefs.preparationPresets) { preparation = $0 }
            case .buffer:
                MinutePickerSheet(title: "기본 여유 시간", message: "새 일정을 만들 때 처음 채워지는 여유 시간이에요.", initial: buffer,
                                  range: Prefs.bufferRange, presets: Prefs.bufferPresets) { buffer = $0 }
            case .snooze:
                ChoiceSheet(title: "다시 울림 간격", options: Prefs.snoozeOptions.map { ($0, "\($0)분") }, selected: snooze) { snooze = $0 }
            case .widget:
                ChoiceSheet(title: "위젯 배경", options: ColorTheme.allCases.map { ($0.rawValue, $0.longLabel) }, selected: widgetTheme) {
                    widgetTheme = $0
                    WidgetBridge.update(store.alarms)
                }
            }
        }
    }

    private var permissionText: String {
        switch notifications {
        case .authorized, .provisional, .ephemeral: return "허용됨"
        case .denied: return "권한 필요"
        default: return "허용 안 됨"
        }
    }

    private var divider: some View {
        Rectangle().fill(AppColor.outline).frame(height: 1.dp).padding(.horizontal, 16.dp)
    }

    private func group<Content: View>(_ title: String, @ViewBuilder _ content: () -> Content) -> some View {
        let inner = content()
        return VStack(alignment: .leading, spacing: 0) {
            SectionLabel(text: title).padding(.bottom, 10.dp)
            GroupCard { inner }
        }
    }

    private func rowLabel(_ icon: String, _ title: String, _ body: String?) -> some View {
        HStack(spacing: 14.dp) {
            Image(systemName: icon).font(.system(size: 18.dp)).foregroundStyle(AppColor.textPrimary).frame(width: 21.dp)
            VStack(alignment: .leading, spacing: 2.dp) {
                Text(title).font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary).lineLimit(1)
                if let body { Text(body).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).fixedSize(horizontal: false, vertical: true) }
            }
            Spacer(minLength: 8.dp)
        }
    }

    private func trailingValue(_ value: String?, _ color: Color?) -> some View {
        HStack(spacing: 4.dp) {
            if let value { Text(value).font(AppFont.bodyMedium).foregroundStyle(color ?? AppColor.textSecondary).lineLimit(1) }
            Image(systemName: "chevron.right").font(.system(size: 14.dp, weight: .semibold)).foregroundStyle(AppColor.textTertiary)
                .frame(width: 22.dp)
        }
    }

    private func row(_ icon: String, _ title: String, body: String? = nil, value: String? = nil, valueColor: Color? = nil,
                     action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 0) {
                rowLabel(icon, title, body)
                trailingValue(value, valueColor)
            }
            .padding(.leading, 16.dp).padding(.trailing, 12.dp).padding(.vertical, 6.dp)
            .frame(minHeight: 54.dp)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func link<Destination: View>(_ icon: String, _ title: String, body: String? = nil, value: String? = nil,
                                         @ViewBuilder destination: @escaping () -> Destination) -> some View {
        NavigationLink(destination: destination) {
            HStack(spacing: 0) {
                rowLabel(icon, title, body)
                trailingValue(value, nil)
            }
            .padding(.leading, 16.dp).padding(.trailing, 12.dp).padding(.vertical, 6.dp)
            .frame(minHeight: 54.dp)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func toggleRow(_ icon: String, _ title: String, body: String? = nil, isOn: Binding<Bool>) -> some View {
        HStack(spacing: 0) {
            rowLabel(icon, title, body)
            AppSwitch(isOn: isOn)
        }
        .padding(.leading, 16.dp).padding(.trailing, 12.dp).padding(.vertical, 6.dp)
        .frame(minHeight: 54.dp)
    }

    private func metric(_ text: String) -> some View {
        Text(text).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }
}

/// 테마(안드로이드 UI-F-002): 3가지 미리보기 카드 중 고르고 [적용하기]
struct ThemeView: View {
    @AppStorage(ColorTheme.storageKey) private var theme = ColorTheme.standard.rawValue
    @State private var selected: ColorTheme = .current
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            BackHeader(title: "테마") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    SectionLabel(text: "컬러 테마").padding(.bottom, 10.dp)
                    HStack(spacing: 8.dp) {
                        ForEach(ColorTheme.allCases) { option in
                            ThemeCard(theme: option, selected: option == selected) { selected = option }
                        }
                    }
                    Spacer().frame(height: 20.dp)
                    notice("mappin.and.ellipse", "지하철 노선 색은 공식 노선색으로 고정되어 테마와 관계없이 같은 색으로 보여요.")
                    Spacer().frame(height: 10.dp)
                    notice("moon", "기기의 다크 모드를 켜면 어두운 화면으로 보여요. 나이트는 항상 어두운 화면이에요.")
                }
                .padding(.horizontal, Space.screen)
                .padding(.top, 8.dp)
            }
            PrimaryButton(title: "적용하기") { theme = selected.rawValue }
                .padding(.horizontal, Space.screen)
                .padding(.bottom, 12.dp)
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func notice(_ icon: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10.dp) {
            Image(systemName: icon).font(.system(size: 15.dp)).foregroundStyle(AppColor.textSecondary)
            Text(text).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
        }
    }
}

/// 테마 미리보기 카드: 그 테마의 배경·버튼·노선 모양
private struct ThemeCard: View {
    let theme: ColorTheme
    let selected: Bool
    let action: () -> Void

    var body: some View {
        let p = Palette.of(theme, dark: theme == .night)
        Button(action: action) {
            VStack(alignment: .leading, spacing: 10.dp) {
                VStack(alignment: .leading, spacing: 8.dp) {
                    Capsule().fill(Color(hex: p.accent == 0x1B2140 ? p.button : p.accent)).frame(width: 34.dp, height: 8.dp)
                    HStack(spacing: 0) {
                        Circle().fill(Color(hex: theme == .night ? p.accent : 0xFF5A1F)).frame(width: 7.dp, height: 7.dp)
                        Rectangle().fill(SampleLineColors.line8).frame(height: 2.dp)
                        Circle().fill(Color(hex: p.textPrimary)).frame(width: 7.dp, height: 7.dp)
                        Rectangle().fill(SampleLineColors.line2).frame(height: 2.dp)
                        Circle().fill(Color(hex: theme == .night ? p.accent : 0xFF5A1F)).frame(width: 7.dp, height: 7.dp)
                    }
                    Capsule().fill(Color(hex: p.dotted)).frame(width: 50.dp, height: 4.dp)
                    Capsule().fill(Color(hex: p.dotted)).frame(width: 34.dp, height: 4.dp)
                }
                .padding(12.dp)
                .frame(maxWidth: .infinity, minHeight: 82.dp, alignment: .topLeading)
                .background(RoundedRectangle(cornerRadius: 12.dp).fill(Color(hex: p.background)))
                .overlay(RoundedRectangle(cornerRadius: 12.dp).strokeBorder(Color(hex: p.outline), lineWidth: 1))
                Text(theme.label).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
            }
            .padding(8.dp)
            .background(RoundedRectangle(cornerRadius: 16.dp).fill(AppColor.background))
            .overlay(RoundedRectangle(cornerRadius: 16.dp).strokeBorder(selected ? AppColor.accent : AppColor.outline, lineWidth: selected ? 2.dp : 1.dp))
            .overlay(alignment: .topTrailing) {
                if selected {
                    Image(systemName: "checkmark").font(.system(size: 11.dp, weight: .bold)).foregroundStyle(AppColor.onAccent)
                        .frame(width: 22.dp, height: 22.dp).background(Circle().fill(AppColor.accent)).offset(x: 6.dp, y: -6.dp)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(theme.label) 테마")
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 아직 iOS 로 옮기는 중인 화면(안드로이드에는 있음)
struct PendingFeatureView: View {
    let title: String
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: title) { dismiss() }
            Spacer()
            Text("안드로이드 앱과 같은 화면으로 옮기는 중이에요.").font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary)
            Spacer()
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// 아래에 잠깐 뜨는 안내(안드로이드 스낵바): 검정 바탕, 3초 뒤 사라짐
struct Snackbar: View {
    let text: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil
    let onDismiss: () -> Void

    var body: some View {
        HStack {
            Text(text).font(AppFont.bodyMedium).foregroundStyle(AppColor.background)
            Spacer()
            if let actionTitle, let action {
                Button(actionTitle) {
                    action()
                    onDismiss()
                }
                .font(AppFont.labelLarge).foregroundStyle(AppColor.accent)
            }
        }
        .padding(.horizontal, 16.dp)
        .frame(minHeight: 48.dp)
        .background(RoundedRectangle(cornerRadius: 8.dp).fill(AppColor.textPrimary))
        .padding(.horizontal, 12.dp)
        .padding(.bottom, 12.dp)
        .task {
            try? await Task.sleep(for: .seconds(3))
            onDismiss()
        }
    }
}
