import SwiftUI
import Shared

/// 기상 → 출발 → 역 → 환승 → 도착 노선도(안드로이드 RouteTimelineView 와 같은 크기: 시각 열 64dp, 레일 56dp, 선 5dp).
/// 지점·구간·문구는 shared 의 RouteLineBuilder 가 만든다.
struct RouteLineView: View {
    let line: RouteLine

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(line.nodes.enumerated()), id: \.offset) { index, node in
                let before = index > 0 ? line.legs[safe: index - 1] : nil
                let after = line.legs[safe: index]
                NodeRow(node: node, before: before, after: after)
                if let after { LegRow(leg: after) }
            }
        }
    }
}

private var timeWidth: CGFloat { 64.dp }
private var railWidth: CGFloat { 56.dp }

private struct RailStyle {
    let color: Color
    var dashed = false

    init?(_ leg: RouteLeg?) {
        guard let leg else { return nil }
        switch leg.kind {
        case .preparation: color = AppColor.dotted; dashed = true
        case .walk: color = AppColor.walk
        default: color = leg.colorArgb.map { Color(argb: $0.int64Value) } ?? AppColor.textSecondary
        }
    }
}

/// 세로 선. 위 절반과 아래 절반 색이 다를 수 있다.
private struct Rail: View {
    let upper: RailStyle?
    let lower: RailStyle?

    var body: some View {
        GeometryReader { geo in
            let x = geo.size.width / 2
            let mid = geo.size.height / 2
            ZStack {
                if let upper { segment(upper, x, 0, mid) }
                if let lower { segment(lower, x, mid, geo.size.height) }
            }
        }
        .frame(width: railWidth)
    }

    private func segment(_ style: RailStyle, _ x: CGFloat, _ from: CGFloat, _ to: CGFloat) -> some View {
        Path { path in
            path.move(to: CGPoint(x: x, y: from))
            path.addLine(to: CGPoint(x: x, y: to))
        }
        .stroke(style.color, style: StrokeStyle(lineWidth: 5.dp, dash: style.dashed ? [6.dp, 5.dp] : []))
    }
}

private struct NodeRow: View {
    let node: RouteNode
    let before: RouteLeg?
    let after: RouteLeg?

    var body: some View {
        HStack(spacing: 0) {
            Text(TimeText.hm(Date(millis: node.timeMillis)))
                .font(AppFont.nodeTime).monospacedDigit()
                .foregroundStyle(node.isAlarmPoint ? AppColor.accentText : AppColor.textPrimary)
                .lineLimit(1)
                .frame(width: timeWidth, alignment: .trailing)
            ZStack {
                Rail(upper: RailStyle(before), lower: RailStyle(after))
                dot
            }
            .frame(width: railWidth)
            VStack(alignment: .leading, spacing: 0) {
                Text(node.title).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary).lineLimit(1)
                if let subtitle = node.subtitle {
                    Text(subtitle).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(2)
                }
            }
            .padding(.vertical, node.isAlarmPoint ? 0 : 3.dp)
            Spacer(minLength: 0)
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var dot: some View {
        if node.isAlarmPoint {
            AlarmDot()
        } else if let argb = node.colorArgb {
            RingDot(color: Color(argb: argb.int64Value))
        } else if node.kind == .alight {
            RingDot(color: AppColor.walk)
        } else {
            RingDot(color: AppColor.textPrimary)
        }
    }
}

private struct LegRow: View {
    let leg: RouteLeg

    var body: some View {
        let style = RailStyle(leg)
        HStack(spacing: 0) {
            Spacer().frame(width: timeWidth)
            Rail(upper: style, lower: style)
            Group {
                if leg.kind == .transit, let argb = leg.colorArgb {
                    let color = Color(argb: argb.int64Value)
                    Pill(text: leg.label, foreground: color, background: color.opacity(0.13))
                } else {
                    Pill(text: leg.label)
                }
            }
            .padding(.vertical, leg.kind == .transit ? 14.dp : 8.dp)
            Spacer(minLength: 0)
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
