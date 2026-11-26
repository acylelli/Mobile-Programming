import SwiftUI
import UIKit

/// 안드로이드 앱(core/designsystem/theme/Color.kt)과 같은 색 토큰. 라이트/다크 자동
enum AppColor {
    static let background = dynamic(0xFFFFFF, 0x121212)
    static let surface = dynamic(0xF5F4F1, 0x1E1E1C)
    static let surfaceStrong = dynamic(0xF0EFEC, 0x2A2A27)
    static let textPrimary = dynamic(0x111111, 0xF5F4F1)
    static let textSecondary = dynamic(0x6B6B6B, 0xA8A6A0)
    static let textTertiary = dynamic(0x9A9A9A, 0x7C7A75)
    static let divider = dynamic(0xECEAE5, 0x2A2A27)
    static let dotted = dynamic(0xCFCBC3, 0x4A4843)
    static let accent = Color(hex: 0xFF5A1F)
    static let accentText = dynamic(0xC2410C, 0xFF8A5B)
    static let accentContainer = dynamic(0xFFF4EF, 0x3A1C10)
    static let button = dynamic(0x111111, 0xF5F4F1)
    static let onButton = dynamic(0xFFFFFF, 0x111111)
    static let danger = dynamic(0xDC2626, 0xF87171)
    static let dangerContainer = dynamic(0xFDECEC, 0x3B1414)
    static let walk = dynamic(0xA3A3A3, 0x6E6C67)
    static let switchOff = dynamic(0xD6D3CD, 0x45433F)

    private static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
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

/// 안드로이드 Typography 에 맞춘 글자
enum AppFont {
    static let headline = Font.app(28, .bold)
    static let title = Font.app(22, .bold)
    static let nodeTitle = Font.app(17, .semibold)
    static let body = Font.app(15)
    static let caption = Font.app(13)
    static let label = Font.app(14, .semibold)
    static let nodeTime = Font.app(16, .medium).monospacedDigit()
}

/// 검은 큰 버튼(다음, 이 시간으로 알람 설정)
struct PrimaryButton: View {
    let title: String
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.app(17, .bold))
                .frame(maxWidth: .infinity, minHeight: 56)
                .foregroundStyle(enabled ? AppColor.onButton : AppColor.textTertiary)
                .background(RoundedRectangle(cornerRadius: 28).fill(enabled ? AppColor.button : AppColor.surfaceStrong))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// 둥근 알약(준비 30분, 76번 21분, 교통 정상)
struct Pill: View {
    let text: String
    var systemImage: String? = nil
    var foreground: Color = AppColor.textSecondary
    var background: Color = AppColor.surfaceStrong

    var body: some View {
        HStack(spacing: 4) {
            if let systemImage { Image(systemName: systemImage).font(.system(size: 12, weight: .bold)) }
            Text(text)
        }
        .font(AppFont.label)
        .foregroundStyle(foreground)
        .padding(.horizontal, 14)
        .padding(.vertical, 7)
        .background(Capsule().fill(background))
    }
}

/// 선택 칩(한 번·평일·매일, 15분·30분)
struct ChoiceChip: View {
    let text: String
    let selected: Bool
    var square = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.app(16, selected ? .bold : .medium))
                .foregroundStyle(selected ? AppColor.onButton : AppColor.textSecondary)
                .frame(minWidth: square ? 40 : nil, minHeight: square ? 40 : 38)
                .padding(.horizontal, square ? 0 : 18)
                .background(RoundedRectangle(cornerRadius: square ? 10 : 19).fill(selected ? AppColor.button : AppColor.surface))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// 기상·도착 지점: 알람 아이콘이 들어간 강조색 점
struct AlarmDot: View {
    var size: CGFloat = 36
    var body: some View {
        ZStack {
            Circle().fill(AppColor.background)
            Circle().stroke(AppColor.accent, lineWidth: 1.5)
            Circle().fill(AppColor.accent).padding(size * 0.11)
            Image(systemName: "alarm").font(.system(size: size * 0.38, weight: .bold)).foregroundStyle(.white)
        }
        .frame(width: size, height: size)
    }
}

/// 역·정류장: 노선색 고리
struct RingDot: View {
    let color: Color
    var size: CGFloat = 20
    var body: some View {
        Circle().strokeBorder(color, lineWidth: 5).background(Circle().fill(AppColor.background)).frame(width: size, height: size)
    }
}

/// 위쪽 진행 막대와 "1/5"
struct StepHeader: View {
    let step: Int
    let total: Int
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").font(.system(size: 20, weight: .medium)) }
                    .foregroundStyle(AppColor.textPrimary)
                    .accessibilityLabel("뒤로")
                Spacer()
                Text("\(step)/\(total)").font(AppFont.label).foregroundStyle(AppColor.textSecondary)
            }
            HStack(spacing: 6) {
                ForEach(1...total, id: \.self) { index in
                    Capsule().fill(index <= step ? AppColor.button : AppColor.surfaceStrong).frame(height: 4)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }
}

/// 회색 둥근 검색창
struct SearchField: View {
    let prompt: String
    @Binding var text: String
    /// 처음 열 때 키보드를 올릴지(이미 고른 장소가 있으면 올리지 않는다)
    var autoFocus = true
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(AppColor.textTertiary)
            TextField(prompt, text: $text)
                .focused($focused)
                .submitLabel(.search)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark").foregroundStyle(AppColor.textSecondary) }
                    .accessibilityLabel("지우기")
            }
        }
        .font(.app(17))
        .padding(.horizontal, 18)
        .frame(height: 54)
        .background(RoundedRectangle(cornerRadius: 27).fill(AppColor.surface))
        .onAppear { if autoFocus { DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { focused = true } } }
    }
}

/// 섹션 제목(경로, 반복, 검색 결과)
struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text).font(.app(15, .semibold)).foregroundStyle(AppColor.textSecondary)
    }
}
