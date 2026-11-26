import AudioToolbox
import SwiftUI
import Shared

/// 기상 알람 화면(안드로이드 UI-D-003): 강조색 전체 화면, 지금 시각, 오늘의 경로, 끄기·다시 울림
struct RingingView: View {
    @EnvironmentObject private var store: AlarmStore
    let alarmId: UUID
    @State private var now = Date()
    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var alarm: Alarm? { store.alarms.first { $0.id == alarmId } }

    var body: some View {
        let veil = isDark(ColorTheme.current) ? Color.white.opacity(0.18) : Color.black.opacity(0.22)
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 0) {
                    Spacer().frame(height: 52.dp)
                    Text("일어날 시간").font(AppFont.titleLarge).foregroundStyle(AppColor.onAccent)
                    Text(TimeText.hm(now)).font(AppFont.ringTime).foregroundStyle(AppColor.onAccent).lineLimit(1).monospacedDigit()
                    Text(subtitle).font(AppFont.labelLarge).foregroundStyle(AppColor.onAccent).multilineTextAlignment(.center)
                    if let alarm, let plan = alarm.plan {
                        Spacer().frame(height: 28.dp)
                        TodayRouteCard(plan: plan, line: plan.routeLine(origin: alarm.origin.name, destination: alarm.destination.name))
                    }
                    Spacer().frame(height: 16.dp)
                }
            }
            Spacer().frame(height: 20.dp)
            Button {
                stopSound()
                store.dismissRinging(alarmId)
            } label: {
                Text("끄기").font(.app(27, .heavy)).foregroundStyle(AppColor.accentText)
                    .frame(width: 112.dp, height: 112.dp)
                    .background(Circle().fill(AppColor.background).shadow(color: .black.opacity(0.25), radius: 14.dp, y: 6.dp))
            }
            .buttonStyle(.plain)
            Spacer().frame(height: 28.dp)
            HStack(spacing: 12.dp) {
                ForEach([5, 10], id: \.self) { minutes in
                    Button {
                        stopSound()
                        Task { await store.snooze(alarmId, minutes: minutes) }
                    } label: {
                        Text("\(minutes)분 후 다시").font(AppFont.titleMedium).foregroundStyle(AppColor.onAccent)
                            .padding(.horizontal, 20.dp).frame(height: 50.dp)
                            .background(Capsule().fill(veil))
                    }
                    .buttonStyle(.plain)
                }
            }
            Spacer().frame(height: 28.dp)
        }
        .padding(.horizontal, Space.screen)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColor.accent.ignoresSafeArea())
        .onReceive(ticker) { now = $0 }
        .onAppear(perform: startSound)
        .onDisappear(perform: stopSound)
    }

    private var subtitle: String {
        let date = TimeText.fullDay(now)
        guard let alarm else { return date }
        return "\(date) | \(alarm.destination.name) \(alarm.arrivalText)"
    }

    private func isDark(_ theme: ColorTheme) -> Bool { theme == .dawn }

    /// 일정(또는 설정)의 알람음을 정한 크기로 되풀이하고, 진동을 켰으면 함께 울린다.
    private func startSound() {
        AlarmSoundPlayer.shared.start(alarm?.effectiveSound ?? .preferences, vibrate: Prefs.bool(Prefs.vibration))
    }

    private func stopSound() {
        AlarmSoundPlayer.shared.stop()
    }
}

/// 오늘의 경로 카드: 출발 → (환승/승차) → 도착 가로 노선
private struct TodayRouteCard: View {
    let plan: SavedPlan
    let line: RouteLine

    var body: some View {
        let nodes = line.nodes.filter { $0.kind != .wakeUp && $0.kind != .alight }
        let departure = nodes.first
        let arrival = nodes.last
        let middle = nodes.dropFirst().dropLast().first
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("오늘의 경로").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                Spacer()
                StatusPill(plan: plan)
            }
            Spacer().frame(height: 14.dp)
            RouteStripView(stops: stops(nodes), links: links())
            Spacer().frame(height: 10.dp)
            HStack(alignment: .top) {
                if let departure { label(departure, "출발", .leading) }
                Spacer()
                if let middle {
                    label(middle, middle.kind == .transfer ? middle.title.replacingOccurrences(of: "역 환승", with: " 환승") : "\(middle.title) 승차", .center)
                }
                Spacer()
                if let arrival { label(arrival, "도착", .trailing) }
            }
        }
        .padding(.horizontal, 18.dp).padding(.vertical, 16.dp)
        .background(RoundedRectangle(cornerRadius: 24.dp).fill(AppColor.background))
    }

    private func stops(_ nodes: [RouteNode]) -> [RouteStripView.Stop] {
        nodes.map { node in
            if node.kind == .arrival { return .init(isAlarm: true, color: AppColor.accent) }
            if let argb = node.colorArgb { return .init(isAlarm: false, color: Color(argb: argb.int64Value)) }
            return .init(isAlarm: false, color: AppColor.textPrimary)
        }
    }

    /// 출발 이후 구간을 지점 사이 선으로 묶는다(하차 뒤 도보는 앞 선에 합친다).
    private func links() -> [RouteStripView.Link] {
        var result: [RouteStripView.Link] = []
        let nodes = line.nodes
        let legs = line.legs
        for index in 1..<nodes.count where index < legs.count + 1 {
            guard index < legs.count else { break }
            let leg = legs[index]
            let color: Color = leg.kind == .walk ? AppColor.walk : leg.colorArgb.map { Color(argb: $0.int64Value) } ?? AppColor.textSecondary
            if nodes[index].kind == .alight, let last = result.popLast() {
                result.append(.init(color: last.color, weight: last.weight + CGFloat(leg.minutes)))
            } else {
                result.append(.init(color: color, weight: CGFloat(leg.minutes)))
            }
        }
        return result
    }

    private func label(_ node: RouteNode, _ caption: String, _ alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 2.dp) {
            Text(TimeText.hm(Date(millis: node.timeMillis))).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
            Text(caption).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(1)
        }
    }
}
