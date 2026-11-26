import SwiftUI
import UIKit

/* ───────────────────────── 화면 비율 ─────────────────────────
 * 안드로이드 화면(Pixel 10 Pro, 1280px · 480dpi = 426.7dp 폭)과 같은 비율로 보이게,
 * 안드로이드 코드의 dp·sp 값에 (아이폰 폭 / 426.7) 을 곱해 쓴다. 예: 24.dp → iPhone 17 Pro(402pt) 에서 22.6pt
 */
enum Dp {
    static let androidWidth: CGFloat = 426.7
    static let scale: CGFloat = {
        let width = min(UIScreen.main.bounds.width, UIScreen.main.bounds.height)
        return width / androidWidth
    }()
}

extension Int { var dp: CGFloat { CGFloat(self) * Dp.scale } }
extension Double { var dp: CGFloat { CGFloat(self) * Dp.scale } }

/* ───────────────────────── 컬러 테마 ───────────────────────── */

/// 안드로이드 ColorTheme 과 같은 3종. 나이트는 항상 어둡다.
enum ColorTheme: String, CaseIterable, Identifiable {
    case standard = "DEFAULT", dawn = "DAWN", night = "NIGHT"
    var id: String { rawValue }

    var label: String {
        switch self {
        case .standard: return "기본"
        case .dawn: return "새벽"
        case .night: return "나이트"
        }
    }

    /// 설정 화면 값 표시("기본 (오렌지)")
    var longLabel: String { self == .standard ? "기본 (오렌지)" : label }

    static let storageKey = "colorTheme"
    static var current: ColorTheme {
        ColorTheme(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .standard
    }
}

/// 안드로이드 AppColors 와 같은 역할 토큰
struct Palette {
    var background: UInt32, surface: UInt32, surfaceStrong: UInt32
    var textPrimary: UInt32, textSecondary: UInt32, textTertiary: UInt32
    var outline: UInt32, divider: UInt32, dotted: UInt32
    var accent: UInt32, onAccent: UInt32, accentText: UInt32, accentContainer: UInt32
    var button: UInt32, onButton: UInt32
    var danger: UInt32, dangerContainer: UInt32
    var walk: UInt32, switchOff: UInt32

    static let lightNeutral = Palette(
        background: 0xFFFFFF, surface: 0xF5F4F1, surfaceStrong: 0xF0EFEC,
        textPrimary: 0x111111, textSecondary: 0x6B6B6B, textTertiary: 0x9A9A9A,
        outline: 0xE7E5E0, divider: 0xECEAE5, dotted: 0xCFCBC3,
        accent: 0xFF5A1F, onAccent: 0xFFFFFF, accentText: 0xC2410C, accentContainer: 0xFFF4EF,
        button: 0x111111, onButton: 0xFFFFFF, danger: 0xDC2626, dangerContainer: 0xFDECEC,
        walk: 0xA3A3A3, switchOff: 0xD6D3CD
    )

    static let darkNeutral = Palette(
        background: 0x121212, surface: 0x1E1E1C, surfaceStrong: 0x2A2A27,
        textPrimary: 0xF5F4F1, textSecondary: 0xA8A6A0, textTertiary: 0x7C7A75,
        outline: 0x34332F, divider: 0x2A2A27, dotted: 0x4A4843,
        accent: 0xFF5A1F, onAccent: 0xFFFFFF, accentText: 0xFF8A5B, accentContainer: 0x3A1C10,
        button: 0xF5F4F1, onButton: 0x111111, danger: 0xF87171, dangerContainer: 0x3B1414,
        walk: 0x6E6C67, switchOff: 0x45433F
    )

    static func of(_ theme: ColorTheme, dark: Bool) -> Palette {
        switch theme {
        case .standard:
            return dark ? darkNeutral : lightNeutral
        case .dawn:
            if dark {
                var p = darkNeutral
                p.background = 0x14172B; p.surface = 0x1E2240; p.surfaceStrong = 0x272C50
                p.textPrimary = 0xF4EFE8; p.textSecondary = 0xA9A7B8; p.textTertiary = 0x7A7890
                p.outline = 0x30355C; p.divider = 0x272C50; p.dotted = 0x454A72
                p.accent = 0xF2B98B; p.onAccent = 0x1B2140; p.accentText = 0xF2B98B; p.accentContainer = 0x3A2F3A
                p.button = 0xF4EFE8; p.onButton = 0x1B2140; p.walk = 0x6C6F8E; p.switchOff = 0x3A3F66
                return p
            }
            var p = lightNeutral
            p.background = 0xFBF7F2; p.surface = 0xF3EDE4; p.surfaceStrong = 0xECE5DA
            p.textPrimary = 0x1B2140; p.textSecondary = 0x6A6676; p.textTertiary = 0x9A96A3
            p.outline = 0xE6DFD3; p.divider = 0xEDE7DC; p.dotted = 0xCFC8BA
            p.accent = 0x1B2140; p.onAccent = 0xFBF7F2; p.accentText = 0x1B2140; p.accentContainer = 0xE9E6EE
            p.button = 0x1B2140; p.onButton = 0xFBF7F2; p.switchOff = 0xD9D2C6
            return p
        case .night:
            var p = darkNeutral
            p.background = 0x0E1220; p.surface = 0x181D30; p.surfaceStrong = 0x222840
            p.textPrimary = 0xF2F3F7; p.textSecondary = 0xA0A6BA; p.textTertiary = 0x6F7590
            p.outline = 0x2A3048; p.divider = 0x222840; p.dotted = 0x474A56
            p.accent = 0xC8F560; p.onAccent = 0x0E1220; p.accentText = 0xC8F560; p.accentContainer = 0x26331A
            p.button = 0xC8F560; p.onButton = 0x0E1220; p.walk = 0x6F7590; p.switchOff = 0x353B57
            return p
        }
    }
}

/// 현재 테마·다크 모드에 맞는 색. 테마를 바꾸면 루트 뷰를 다시 그려 새 색을 읽는다.
enum AppColor {
    static var background: Color { token(\.background) }
    static var surface: Color { token(\.surface) }
    static var surfaceStrong: Color { token(\.surfaceStrong) }
    static var textPrimary: Color { token(\.textPrimary) }
    static var textSecondary: Color { token(\.textSecondary) }
    static var textTertiary: Color { token(\.textTertiary) }
    static var outline: Color { token(\.outline) }
    static var divider: Color { token(\.divider) }
    static var dotted: Color { token(\.dotted) }
    static var accent: Color { token(\.accent) }
    static var onAccent: Color { token(\.onAccent) }
    static var accentText: Color { token(\.accentText) }
    static var accentContainer: Color { token(\.accentContainer) }
    static var button: Color { token(\.button) }
    static var onButton: Color { token(\.onButton) }
    static var danger: Color { token(\.danger) }
    static var dangerContainer: Color { token(\.dangerContainer) }
    static var walk: Color { token(\.walk) }
    static var switchOff: Color { token(\.switchOff) }

    private static func token(_ key: KeyPath<Palette, UInt32>) -> Color {
        let theme = ColorTheme.current
        return Color(UIColor { traits in
            UIColor(hex: Palette.of(theme, dark: theme == .night || traits.userInterfaceStyle == .dark)[keyPath: key])
        })
    }
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
    }
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }
    /// shared 모듈의 ARGB(Long) 노선색
    init(argb: Int64) { self.init(hex: UInt32(truncatingIfNeeded: argb & 0xFFFFFF)) }
}

/* ───────────────────────── 글자(안드로이드 Typography) ───────────────────────── */

/// 안드로이드 TextStyle: 크기·굵기·줄 높이·자간(모두 sp)
struct AppTextStyle {
    let size: CGFloat
    let weight: Font.Weight
    let lineHeight: CGFloat
    var tracking: CGFloat = 0

    var font: Font { .app(size, weight) }

    /// 안드로이드는 줄마다 lineHeight 만큼 차지한다. 한글 글꼴(Noto Sans KR) 줄 높이와의 차이를 위아래 여백·줄 간격으로 채운다.
    var extraLeading: CGFloat {
        let line = AppFonts.lineHeight(size, weight)
        return max(0, lineHeight * AppFonts.textScale - line)
    }
}

extension View {
    /// 안드로이드 Typography 와 같은 글자 크기·줄 높이·자간
    func font(_ style: AppTextStyle) -> some View {
        let extra = style.extraLeading
        return self.font(style.font)
            .tracking(style.tracking * AppFonts.textScale)
            .lineSpacing(extra)
            .padding(.vertical, extra / 2)
    }
}

enum AppFont {
    static let displayLarge = AppTextStyle(size: 56, weight: .regular, lineHeight: 62, tracking: -1)
    static let displayMedium = AppTextStyle(size: 40, weight: .regular, lineHeight: 46, tracking: -0.5)
    static let headlineLarge = AppTextStyle(size: 26, weight: .heavy, lineHeight: 34, tracking: -0.5)
    static let headlineMedium = AppTextStyle(size: 23, weight: .heavy, lineHeight: 31, tracking: -0.3)
    static let headlineSmall = AppTextStyle(size: 20, weight: .bold, lineHeight: 27)
    static let titleLarge = AppTextStyle(size: 20, weight: .bold, lineHeight: 28)
    static let titleMedium = AppTextStyle(size: 17, weight: .bold, lineHeight: 24)
    static let titleSmall = AppTextStyle(size: 14, weight: .bold, lineHeight: 20)
    static let bodyLarge = AppTextStyle(size: 16, weight: .regular, lineHeight: 24)
    static let bodyMedium = AppTextStyle(size: 14, weight: .regular, lineHeight: 21)
    static let bodySmall = AppTextStyle(size: 13, weight: .regular, lineHeight: 19)
    static let labelLarge = AppTextStyle(size: 15, weight: .bold, lineHeight: 20)
    static let labelMedium = AppTextStyle(size: 13, weight: .bold, lineHeight: 18)
    static let labelSmall = AppTextStyle(size: 11, weight: .bold, lineHeight: 14)
    static let ringTime = AppTextStyle(size: 88, weight: .light, lineHeight: 92, tracking: -3)
    static let listTime = AppTextStyle(size: 52, weight: .regular, lineHeight: 58, tracking: -0.5)
    static let listMeridiem = AppTextStyle(size: 26, weight: .regular, lineHeight: 32)
    static let nodeTime = AppTextStyle(size: 18, weight: .medium, lineHeight: 24, tracking: -0.5)
    static let figure = AppTextStyle(size: 20, weight: .medium, lineHeight: 26, tracking: -0.5)
    static let button = AppTextStyle(size: 17, weight: .bold, lineHeight: 24)
    static let tab = AppTextStyle(size: 12, weight: .medium, lineHeight: 16)
}

/// 안드로이드 Spacing / Radius
enum Space {
    static var xs: CGFloat { 4.dp }
    static var sm: CGFloat { 8.dp }
    static var md: CGFloat { 16.dp }
    static var lg: CGFloat { 24.dp }
    static var xl: CGFloat { 32.dp }
    static var xxl: CGFloat { 48.dp }
    /// 화면 좌우 여백
    static var screen: CGFloat { 24.dp }
}

enum Radius {
    static var card: CGFloat { 22.dp }
    static var group: CGFloat { 20.dp }
    static var button: CGFloat { 20.dp }
    static var field: CGFloat { 18.dp }
    static var tile: CGFloat { 12.dp }
}

/* ───────────────────────── 공통 부품 ───────────────────────── */

/// 화면의 주 행동 버튼(검정 60dp)
struct PrimaryButton: View {
    let title: String
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(AppFont.button)
                .lineLimit(1)
                .frame(maxWidth: .infinity, minHeight: 60.dp)
                .foregroundStyle(enabled ? AppColor.onButton : AppColor.textTertiary)
                .background(RoundedRectangle(cornerRadius: Radius.button).fill(enabled ? AppColor.button : AppColor.surfaceStrong))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// 보조 행동(다시 계산, 지도 열기): 테두리만 있는 52dp 버튼
struct SecondaryButton: View {
    let title: String
    var danger = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(AppFont.labelLarge)
                .lineLimit(1)
                .foregroundStyle(danger ? AppColor.danger : AppColor.textPrimary)
                .frame(maxWidth: .infinity, minHeight: 52.dp)
                .overlay(
                    RoundedRectangle(cornerRadius: Radius.button)
                        .strokeBorder(danger ? AppColor.danger.opacity(0.4) : AppColor.outline, lineWidth: 1.dp)
                )
        }
        .buttonStyle(.plain)
    }
}

/// 둥근 알약(교통 정상, 준비 30분, 76번 21분). 안드로이드 Pill: labelMedium, 가로 11(아이콘 10)·세로 5dp
struct Pill: View {
    let text: String
    var systemImage: String? = nil
    var foreground: Color = AppColor.textSecondary
    var background: Color = AppColor.surfaceStrong
    var font: AppTextStyle = AppFont.labelMedium

    var body: some View {
        HStack(spacing: 4.dp) {
            if let systemImage {
                Image(systemName: systemImage).font(.system(size: 12.dp, weight: .bold)).frame(width: 15.dp, height: 15.dp)
            }
            Text(text).lineLimit(1)
        }
        .font(font)
        .foregroundStyle(foreground)
        .padding(.horizontal, systemImage == nil ? 11.dp : 10.dp)
        .padding(.vertical, 5.dp)
        .background(Capsule().fill(background))
    }
}

/// 작은 꼬리표(무료, PRO)
struct TagBadge: View {
    let text: String
    var foreground: Color = AppColor.textSecondary
    var background: Color = AppColor.surfaceStrong

    var body: some View {
        Text(text).font(AppFont.labelSmall).foregroundStyle(foreground)
            .padding(.horizontal, 8.dp).padding(.vertical, 3.dp)
            .background(Capsule().fill(background))
    }
}

/// 선택 알약(한 번·평일·매일, 15분·30분): 높이 40dp
struct ChoicePill: View {
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(AppFont.labelLarge)
                .foregroundStyle(selected ? AppColor.onButton : AppColor.textSecondary)
                .padding(.horizontal, 16.dp)
                .frame(height: 40.dp)
                .background(Capsule().fill(selected ? AppColor.button : AppColor.surface))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 요일 칸: 높이 36dp, 모서리 10dp
struct DayChip: View {
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(AppFont.labelLarge)
                .foregroundStyle(selected ? AppColor.onButton : AppColor.textTertiary)
                .frame(maxWidth: .infinity, minHeight: 36.dp)
                .background(RoundedRectangle(cornerRadius: 10.dp).fill(selected ? AppColor.button : AppColor.surface))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(text)요일")
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 안드로이드 AppSwitch: 54×30dp, 손잡이 24dp
struct AppSwitch: View {
    @Binding var isOn: Bool

    var body: some View {
        Button {
            withAnimation(.easeOut(duration: 0.15)) { isOn.toggle() }
        } label: {
            ZStack(alignment: isOn ? .trailing : .leading) {
                Capsule().fill(isOn ? AppColor.accent : AppColor.switchOff)
                Circle().fill(Color.white).frame(width: 24.dp, height: 24.dp).padding(3.dp)
                    .shadow(color: .black.opacity(0.12), radius: 1, y: 1)
            }
            .frame(width: 54.dp, height: 30.dp)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(isOn ? "켬" : "끔")
    }
}

/// 기상·도착 지점: 알람 아이콘이 들어간 강조색 점(36dp)
struct AlarmDot: View {
    var size: CGFloat = 36.dp
    var body: some View {
        ZStack {
            Circle().fill(AppColor.background)
            Circle().strokeBorder(AppColor.accent, lineWidth: 1.5.dp)
            Circle().fill(AppColor.accent).padding(size * 0.11)
            Image(systemName: "alarm").font(.system(size: size * 0.36, weight: .bold)).foregroundStyle(AppColor.onAccent)
        }
        .frame(width: size, height: size)
    }
}

/// 역·정류장: 노선색 고리(20dp, 두께 5dp)
struct RingDot: View {
    let color: Color
    var size: CGFloat = 20.dp
    var stroke: CGFloat = 5.dp
    var body: some View {
        Circle().strokeBorder(color, lineWidth: stroke).background(Circle().fill(AppColor.background)).frame(width: size, height: size)
    }
}

/// 탭 화면 제목(알람, 설정): headlineLarge, 위 20dp
struct ScreenHeader<Trailing: View>: View {
    let title: String
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(alignment: .center) {
            Text(title).font(AppFont.headlineLarge).foregroundStyle(AppColor.textPrimary)
            Spacer()
            trailing()
        }
        .padding(.horizontal, Space.screen)
        .padding(.top, 20.dp)
        .padding(.bottom, 8.dp)
    }
}

extension ScreenHeader where Trailing == EmptyView {
    init(title: String) { self.init(title: title) { EmptyView() } }
}

/// 뒤로 가기 제목줄(일정 상세, 테마, 기록·통계): 높이 56dp, titleLarge
struct BackHeader<Trailing: View>: View {
    let title: String
    let onBack: () -> Void
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 2.dp) {
            Button(action: onBack) {
                Image(systemName: "chevron.left").font(.system(size: 18.dp, weight: .semibold))
                    .frame(width: 48.dp, height: 48.dp)
            }
            .accessibilityLabel("뒤로")
            Text(title).font(AppFont.titleLarge).lineLimit(1)
            Spacer()
            trailing()
        }
        .foregroundStyle(AppColor.textPrimary)
        .frame(height: 56.dp)
        .padding(.leading, Space.screen - 14.dp)
        .padding(.trailing, Space.screen - 12.dp)
    }
}

extension BackHeader where Trailing == EmptyView {
    init(title: String, onBack: @escaping () -> Void) { self.init(title: title, onBack: onBack) { EmptyView() } }
}

/// 위쪽 진행 막대와 "1/5"
struct StepHeader: View {
    let step: Int
    let total: Int
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 8.dp) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 18.dp, weight: .semibold)).frame(width: 48.dp, height: 48.dp)
                }
                .foregroundStyle(AppColor.textPrimary)
                .padding(.leading, -14.dp)
                .accessibilityLabel("뒤로")
                Spacer()
                Text("\(step)/\(total)").font(AppFont.titleSmall).foregroundStyle(AppColor.textSecondary)
            }
            HStack(spacing: 6.dp) {
                ForEach(1...total, id: \.self) { index in
                    Capsule().fill(index <= step ? AppColor.button : AppColor.surfaceStrong).frame(height: 4.dp)
                }
            }
        }
        .padding(.horizontal, Space.screen)
        .padding(.top, 8.dp)
    }
}

/// 회색 둥근 검색창: 높이 54dp, 모서리 18dp
struct SearchField: View {
    let prompt: String
    @Binding var text: String
    /// 처음 열 때 키보드를 올릴지(이미 고른 장소가 있으면 올리지 않는다)
    var autoFocus = true
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10.dp) {
            Image(systemName: "magnifyingglass").font(.system(size: 18.dp)).foregroundStyle(AppColor.textTertiary)
            TextField(prompt, text: $text)
                .font(AppFont.bodyLarge)
                .focused($focused)
                .submitLabel(.search)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark").font(.system(size: 16.dp)).foregroundStyle(AppColor.textSecondary) }
                    .accessibilityLabel("지우기")
            }
        }
        .padding(.horizontal, 16.dp)
        .frame(height: 54.dp)
        .background(RoundedRectangle(cornerRadius: Radius.field).fill(AppColor.surface))
        .onAppear { if autoFocus { DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { focused = true } } }
    }
}

/// 섹션 제목(경로, 반복, 검색 결과): titleSmall, 보조 글자색
struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text).font(AppFont.titleSmall).foregroundStyle(AppColor.textSecondary)
    }
}

/// 회색 묶음 카드(설정 그룹, 계산 내역)
struct GroupCard<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0, content: content)
            .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
    }
}
