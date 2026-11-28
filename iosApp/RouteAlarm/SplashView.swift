import SwiftUI

/// 시작 화면 "노선 그리기"(안드로이드 splash_icon_animated 와 같은 움직임).
/// 실행 화면(바늘 없는 시계) 그대로에서 시작해 8호선 분홍 → 2호선 초록 바늘이 노선처럼 그려지고 역 점이 찍힌 뒤,
/// 시계가 6배로 커지며 사라져 홈 화면이 드러난다. 크기는 안드로이드 시작 화면 아이콘과 같은 237dp(100 격자).
struct SplashView: View {
    let onFinish: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var line1: CGFloat = 0
    @State private var line2: CGFloat = 0
    @State private var dot1: CGFloat = 0
    @State private var dot2: CGFloat = 0
    @State private var zoom: CGFloat = 1
    @State private var fade: Double = 1

    private static let orange = Color(hex: 0xFF5A1F)
    private static let pink = Color(hex: 0xE6186C)
    private static let green = Color(hex: 0x00A84D)
    private static let ink = Color(hex: 0x141418)

    var body: some View {
        ZStack {
            Self.orange
            clock.frame(width: 237.dp, height: 237.dp).scaleEffect(zoom)
        }
        .ignoresSafeArea()
        .opacity(fade)
        .accessibilityHidden(true)
        .task { await play() }
    }

    /// 100 격자 도안(아이콘과 같은 좌표)
    private var clock: some View {
        GeometryReader { geo in
            let u = geo.size.width / 100
            ZStack {
                GridPath { p in
                    // 종: 아이콘의 반지름 12 원호(26,33 → 40,21 / 74,33 → 60,21)
                    p.addArc(center: CGPoint(x: 38, y: 32.83), radius: 12, startAngle: .degrees(179.2), endAngle: .degrees(279.6), clockwise: false)
                    p.move(to: CGPoint(x: 74, y: 33))
                    p.addArc(center: CGPoint(x: 62, y: 32.83), radius: 12, startAngle: .degrees(0.8), endAngle: .degrees(260.4), clockwise: true)
                    p.move(to: CGPoint(x: 36, y: 79)); p.addLine(to: CGPoint(x: 31, y: 85))
                    p.move(to: CGPoint(x: 64, y: 79)); p.addLine(to: CGPoint(x: 69, y: 85))
                }
                .stroke(.white, style: StrokeStyle(lineWidth: 6 * u, lineCap: .round))
                Circle().fill(.white).frame(width: 56 * u, height: 56 * u).position(x: 50 * u, y: 54 * u)
                GridPath { p in p.move(to: CGPoint(x: 50, y: 54)); p.addLine(to: CGPoint(x: 50, y: 37)) }
                    .trim(from: 0, to: line1)
                    .stroke(Self.pink, style: StrokeStyle(lineWidth: 5 * u, lineCap: .round))
                GridPath { p in p.move(to: CGPoint(x: 50, y: 54)); p.addLine(to: CGPoint(x: 63, y: 62)) }
                    .trim(from: 0, to: line2)
                    .stroke(Self.green, style: StrokeStyle(lineWidth: 5 * u, lineCap: .round))
                station(Self.pink, u).scaleEffect(dot1).position(x: 50 * u, y: 37 * u)
                station(Self.green, u).scaleEffect(dot2).position(x: 63 * u, y: 62 * u)
                Circle().fill(Self.ink).frame(width: 10 * u, height: 10 * u).position(x: 50 * u, y: 54 * u)
            }
        }
    }

    private func station(_ color: Color, _ u: CGFloat) -> some View {
        Circle().fill(.white).overlay(Circle().strokeBorder(color, lineWidth: 3 * u)).frame(width: 12 * u, height: 12 * u)
    }

    /// 안드로이드와 같은 시간표: 분홍 0.05~0.27초, 점 0.24초, 초록 0.27~0.49초, 점 0.46초, 0.62초부터 0.38초 동안 확대·사라짐
    private func play() async {
        if reduceMotion {
            line1 = 1; line2 = 1; dot1 = 1; dot2 = 1
            try? await Task.sleep(for: .milliseconds(450))
            withAnimation(.easeOut(duration: 0.25)) { fade = 0 }
            try? await Task.sleep(for: .milliseconds(260))
            onFinish()
            return
        }
        let draw = Animation.timingCurve(0.4, 0, 0.2, 1, duration: 0.22)
        let pop = Animation.spring(response: 0.22, dampingFraction: 0.5)
        withAnimation(draw.delay(0.05)) { line1 = 1 }
        withAnimation(pop.delay(0.24)) { dot1 = 1 }
        withAnimation(draw.delay(0.27)) { line2 = 1 }
        withAnimation(pop.delay(0.46)) { dot2 = 1 }
        try? await Task.sleep(for: .milliseconds(620))
        withAnimation(.easeIn(duration: 0.38)) { zoom = 6 }
        withAnimation(.easeOut(duration: 0.285).delay(0.095)) { fade = 0 }
        try? await Task.sleep(for: .milliseconds(400))
        onFinish()
    }
}

/// 100 격자 좌표로 그린 경로를 뷰 크기에 맞춰 늘린다.
private struct GridPath: Shape {
    let build: (inout Path) -> Void
    func path(in rect: CGRect) -> Path {
        var path = Path()
        build(&path)
        return path.applying(CGAffineTransform(scaleX: rect.width / 100, y: rect.height / 100))
    }
}
