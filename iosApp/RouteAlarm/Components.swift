import SwiftUI

/// 회색 카드(안드로이드 SurfaceCard: surface, 모서리 22dp)
struct SurfaceCard<Content: View>: View {
    var padding: CGFloat = 20.dp
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0, content: content)
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.card).fill(AppColor.surface))
    }
}

/// 테두리 카드(안드로이드 AppCard: 배경색, 1dp 테두리, 선택되면 강조색 1.5dp)
struct OutlineCard<Content: View>: View {
    var selected = false
    var padding: CGFloat = 16.dp
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0, content: content)
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.card).fill(selected ? AppColor.accentContainer : AppColor.background))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.card)
                    .strokeBorder(selected ? AppColor.accent : AppColor.outline, lineWidth: selected ? 1.5.dp : 1.dp)
            )
    }
}

/// "나중에 설정할게요" 같은 밑줄 글자 링크
struct TextLink: View {
    let text: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(text).font(AppFont.bodyLarge).underline().foregroundStyle(AppColor.textSecondary)
                .padding(.horizontal, 16.dp).padding(.vertical, 13.dp)
        }
        .buttonStyle(.plain)
    }
}

/// 가로 노선(온보딩, 기상 알람 "오늘의 경로"): 점과 선을 번갈아 그린다. 선 길이는 weight 비율
struct RouteStripView: View {
    struct Stop { let isAlarm: Bool; let color: Color }
    struct Link { let color: Color; let weight: CGFloat }
    let stops: [Stop]
    let links: [Link]
    var alarmSize: CGFloat = 32.dp
    var ringSize: CGFloat = 18.dp

    var body: some View {
        GeometryReader { geo in
            let dots = stops.reduce(CGFloat(0)) { $0 + ($1.isAlarm ? alarmSize : ringSize) }
            let total = max(1, links.reduce(CGFloat(0)) { $0 + max($1.weight, 0.1) })
            let available = max(0, geo.size.width - dots + CGFloat(links.count) * 4.dp)
            HStack(spacing: -2.dp) {
                ForEach(Array(stops.enumerated()), id: \.offset) { index, stop in
                    Group {
                        if stop.isAlarm { AlarmDot(size: alarmSize) } else { RingDot(color: stop.color, size: ringSize) }
                    }
                    .zIndex(1)
                    if index < links.count {
                        let link = links[index]
                        Rectangle().fill(link.color)
                            .frame(width: available * max(link.weight, 0.1) / total, height: 5.dp)
                    }
                }
            }
            .frame(height: geo.size.height)
        }
        .frame(height: alarmSize)
    }
}

/// 설계서 예시 노선색
enum SampleLineColors {
    static let line8 = Color(hex: 0xE6186C)
    static let line2 = Color(hex: 0x00A84D)
}
