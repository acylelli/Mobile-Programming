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
    static func uiFont(_ size: CGFloat, _ weight: Font.Weight) -> UIFont {
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
