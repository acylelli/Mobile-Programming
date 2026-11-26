import CoreText
import SwiftUI
import UIKit

/// 안드로이드와 같은 글꼴: 숫자·영문은 Roboto, 한글은 Noto Sans KR(안드로이드 기본 글꼴 조합).
/// 두 글꼴 모두 굵기를 바꿀 수 있는 가변 글꼴이라 파일 하나로 모든 굵기를 쓴다(OFL 라이선스, Fonts/OFL-*.txt).
enum AppFonts {
    private static let files = ["Roboto", "NotoSansKR"]
    /// 앱 시작 때 번들의 글꼴을 이 프로세스에 등록한다(Info.plist 없이).
    static func register() {
        for name in files {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    /// 굵기별 이름(가변 글꼴의 이름 붙은 굵기)으로 고른다. 굵기 축 값을 직접 주면 보통 굵기가 가장 얇은 굵기로 잡히는 경우가 있다.
    /// sp → pt 배율: 화면 폭 비율 × 한글 글꼴 보정
    static let textScale: CGFloat = Dp.scale * 1.026

    /// 그 크기·굵기에서 한 줄이 차지하는 높이. SwiftUI 는 첫 글꼴(Roboto)의 줄 높이로 줄을 잡는다.
    static func lineHeight(_ size: CGFloat, _ weight: Font.Weight) -> CGFloat {
        let scaled = size * textScale
        return UIFont(name: "Roboto-\(styleName(weight))", size: scaled)?.lineHeight ?? scaled * 1.17
    }

    /// size 는 안드로이드 sp 값. 화면 폭 비율(Dp.scale)을 곱해 안드로이드와 같은 비율로 보이게 한다.
    static func uiFont(_ size: CGFloat, _ weight: Font.Weight) -> UIFont {
        // 안드로이드 기본 한글 글꼴(Noto Sans CJK)이 같은 크기에서 2.6% 정도 크게 그려져 그만큼 맞춘다(화면 비교로 잰 값).
        let size = size * textScale
        let style = styleName(weight)
        let korean = UIFontDescriptor(name: "NotoSansKR-\(style)", size: size)
        let descriptor = UIFontDescriptor(name: "Roboto-\(style)", size: size).addingAttributes([.cascadeList: [korean]])
        return UIFont(descriptor: descriptor, size: size)
    }

    private static func styleName(_ weight: Font.Weight) -> String {
        switch weight {
        case .ultraLight: return "ExtraLight"
        case .thin: return "Thin"
        case .light: return "Light"
        case .medium: return "Medium"
        case .semibold: return "SemiBold"
        case .bold: return "Bold"
        case .heavy: return "ExtraBold"
        case .black: return "Black"
        default: return "Regular"
        }
    }
}

extension Font {
    /// 안드로이드와 같은 글꼴(Roboto + Noto Sans KR)
    static func app(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        Font(AppFonts.uiFont(size, weight))
    }
}
