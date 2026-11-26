import SwiftUI
import Shared

/// 알람 변경 추천(안드로이드 PendingAdjustmentCard): 변경 전 → 변경 후, [유지하기] [변경하기]
struct PendingAdjustmentCard: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: Alarm

    var body: some View {
        if let pending = alarm.pendingPlan, let current = alarm.plan {
            let delta = Int(pending.wakeUp.timeIntervalSince(current.wakeUp) / 60)
            let magnitude = abs(delta)
            let body: String = {
                if pending.isOffline && delta < 0 { return "실시간 교통 정보를 확인하지 못해 여유를 더했어요. 알람을 \(magnitude)분 앞당길까요?" }
                if alarm.pendingNeedsConfirmation == true { return "예상 이동시간이 크게 바뀌었어요. 알람을 \(magnitude)분 옮길까요?" }
                if delta < 0 { return "교통 상황이 바뀌어 알람을 \(magnitude)분 앞당기는 것을 추천합니다." }
                return "교통 상황이 좋아져 알람을 \(magnitude)분 늦출 수 있어요."
            }()
            OutlineCard(selected: true, padding: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("알람 변경을 추천해요").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                    Spacer().frame(height: 6.dp)
                    HStack(spacing: 8.dp) {
                        Text(TimeText.hm(current.wakeUp)).font(AppFont.figure.font).strikethrough().foregroundStyle(AppColor.textTertiary)
                        Text("→").font(AppFont.titleMedium).foregroundStyle(AppColor.textTertiary)
                        Text(TimeText.hm(pending.wakeUp)).font(AppFont.figure).foregroundStyle(AppColor.accentText)
                    }
                    Spacer().frame(height: 4.dp)
                    Text(body).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).padding(.trailing, 12.dp)
                    HStack {
                        Spacer()
                        InlineAction(text: "유지하기") { store.dismissPending(alarm.id) }
                        InlineAction(text: "변경하기", emphasized: true) { Task { await store.applyPending(alarm.id) } }
                    }
                }
                .padding(.leading, 18.dp).padding(.trailing, 6.dp).padding(.top, 16.dp).padding(.bottom, 4.dp)
            }
        }
    }
}

/// 기상 습관 제안(안드로이드 WakeUpHabitCard)
struct WakeUpHabitCard: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: Alarm

    var body: some View {
        if let habit = store.habit(for: alarm) {
            let extra = Int(habit.extraPreparationMinutes)
            OutlineCard(selected: true, padding: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("기상 습관에 맞춰 볼까요?").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                    Spacer().frame(height: 6.dp)
                    Text("최근 \(habit.samples)번 기상 알람을 평소 \(habit.typicalLateMinutes)분쯤 늦게 끄셨어요. 준비 시간을 \(extra)분 늘리면 그만큼 일찍 깨워 드려서 출발이 늦어지지 않아요.")
                        .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                    Spacer().frame(height: 4.dp)
                    Text("준비 시간 \(alarm.preparationMinutes)분 → \(alarm.preparationMinutes + extra)분").font(AppFont.labelLarge).foregroundStyle(AppColor.accentText)
                    HStack {
                        Spacer()
                        InlineAction(text: "괜찮아요") { store.dismissHabit(alarm) }
                        InlineAction(text: "\(extra)분 늘리기", emphasized: true) { Task { await store.applyHabit(alarm, extra: extra) } }
                    }
                }
                .padding(.leading, 18.dp).padding(.trailing, 6.dp).padding(.top, 16.dp).padding(.bottom, 4.dp)
            }
        }
    }
}

/// 요일별 이동시간(안드로이드 TravelPatternCard): 막대는 가장 오래 걸린 요일 기준, 3분 이상 더 걸리면 강조색
struct TravelPatternCard: View {
    let pattern: TravelPatternSummary

    var body: some View {
        let longest = max(1, pattern.weekdays.map { Int($0.typicalMinutes) }.max() ?? 1)
        InfoCard {
            Text(pattern.slowest.map { "\(TravelHistory.shared.dayName(ordinal: Int32($0.day.ordinal)))요일은 평소보다 \($0.extraMinutes)분 더 걸려요" }
                 ?? "요일마다 비슷하게 약 \(pattern.baselineMinutes)분 걸려요")
                .font(AppFont.labelLarge).foregroundStyle(AppColor.textPrimary)
            ForEach(Array(pattern.weekdays.enumerated()), id: \.offset) { _, day in
                let notable = day.extraMinutes >= 3
                HStack(spacing: 0) {
                    Text(TravelHistory.shared.dayName(ordinal: Int32(day.day.ordinal))).font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
                        .frame(width: 28.dp, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(AppColor.dotted)
                            Capsule().fill(notable ? AppColor.accent : AppColor.textTertiary)
                                .frame(width: geo.size.width * CGFloat(day.typicalMinutes) / CGFloat(longest))
                        }
                    }
                    .frame(height: 10.dp)
                    Spacer().frame(width: 12.dp)
                    Text("\(day.typicalMinutes)분").font(AppFont.labelLarge).foregroundStyle(notable ? AppColor.accentText : AppColor.textPrimary)
                        .frame(width: 44.dp, alignment: .trailing)
                }
            }
            Text("출발 직전에 확인한 실제 이동시간이에요. 같은 요일·시간대 기록이 3번 이상 쌓이면 다음 알람 계산에 반영해요.")
                .font(AppFont.bodySmall).foregroundStyle(AppColor.textTertiary)
        }
    }
}

/// 지각 위험 → 택시 안내(안드로이드 LateRiskCard). 대중교통으로 늦을 근거가 있을 때만 보인다.
struct LateRiskCard: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: Alarm
    @State private var taxi: TaxiEstimate?
    @State private var loaded = false
    private let service = TaxiService(client: ProxyClient(baseUrl: ProxyClient.companion.DEFAULT_BASE_URL, client: nil))

    var body: some View {
        if alarm.enabled, let plan = alarm.plan, let risk = risk(plan) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 10.dp) {
                    Image(systemName: "car.fill").font(.system(size: 17.dp)).foregroundStyle(AppColor.danger)
                    Text(risk.reason == "OVERSLEPT" ? "늦게 일어나서 \(risk.lateMinutes)분 늦을 것 같아요" : "교통이 나빠져 \(risk.lateMinutes)분 늦을 것 같아요")
                        .font(AppFont.titleSmall).foregroundStyle(AppColor.danger)
                }
                Spacer().frame(height: 4.dp)
                Text("\(hm(risk.leaveAtMillis))에 출발하면 대중교통 도착 예상 \(hm(risk.transitArrivalMillis)) (목표 \(hm(risk.targetMillis)))")
                    .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                Spacer().frame(height: 6.dp)
                if !loaded {
                    Text("택시 예상 시간을 확인하고 있어요…").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                } else if let taxi {
                    let arrival = max(Date(), Date(millis: risk.leaveAtMillis)).addingTimeInterval(Double(taxi.minutes) * 60)
                    let taxiLate = Int(arrival.timeIntervalSince(Date(millis: risk.targetMillis)) / 60)
                    Text("택시로 가면 \(TimeText.hm(arrival)) 도착 · 약 \(taxi.minutes)분 · 약 \(won(Int(taxi.fareWon)))원")
                        .font(AppFont.bodyMedium).foregroundStyle(AppColor.textPrimary)
                    Text(taxiLate > 0 ? "택시도 \(taxiLate)분쯤 늦어요. 그래도 대중교통보다는 빨라요." : "택시를 타면 늦지 않아요. 요금은 지금 교통 기준 예상이에요.")
                        .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                } else {
                    Text("택시 예상 시간을 가져오지 못했어요. 카카오 T 에서 바로 확인해 보세요.").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                }
                Spacer().frame(height: 10.dp)
                PrimaryButton(title: "카카오 T로 택시 부르기") { openKakaoT() }
            }
            .padding(16.dp)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.field).fill(AppColor.dangerContainer))
            .task(id: alarm.id) {
                guard !loaded else { return }
                taxi = try? await service.estimate(origin: alarm.origin.shared, destination: alarm.destination.shared)
                loaded = true
            }
        }
    }

    private func risk(_ plan: SavedPlan) -> LateRisk? {
        let dismissal = store.records(for: alarm.id).first { $0.kind == .dismissed }?.event
        return AlarmHistoryRules.shared.lateRisk(
            targetArrivalMillis: plan.targetArrival.millis, departureMillis: plan.departure.millis,
            estimatedArrivalMillis: plan.estimatedArrival.millis, travelMinutes: Int32(plan.travelMinutes),
            targetHour: Int32(alarm.hour), targetMinute: Int32(alarm.minute), preparationMinutes: Int32(alarm.preparationMinutes),
            lastDismissal: dismissal, nowMillis: Date().millis
        )
    }

    private func hm(_ millis: Int64) -> String { TimeText.hm(Date(millis: millis)) }

    private func won(_ amount: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        return formatter.string(from: NSNumber(value: amount)) ?? "\(amount)"
    }

    /// 카카오 T 를 출발지·도착지가 채워진 택시 호출 화면으로 연다. 앱이 없으면 앱스토어로
    private func openKakaoT() {
        let link = "kakaot://taxi/set?origin_lat=\(alarm.origin.latitude)&origin_lng=\(alarm.origin.longitude)&dest_lat=\(alarm.destination.latitude)&dest_lng=\(alarm.destination.longitude)"
        guard let url = URL(string: link) else { return }
        UIApplication.shared.open(url) { opened in
            if !opened, let store = URL(string: "https://apps.apple.com/kr/app/id981110422") { UIApplication.shared.open(store) }
        }
    }
}

/// 글자 버튼(안드로이드 InlineAction): labelLarge, 가로 12·세로 10dp
struct InlineAction: View {
    let text: String
    var emphasized = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(text).font(AppFont.labelLarge).foregroundStyle(emphasized ? AppColor.accentText : AppColor.textSecondary)
                .padding(.horizontal, 12.dp).padding(.vertical, 10.dp)
        }
        .buttonStyle(.plain)
    }
}

/// 기록·통계(안드로이드 StatsScreen)
struct StatsView: View {
    @EnvironmentObject private var store: AlarmStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let stats = store.stats
        VStack(spacing: 0) {
            BackHeader(title: "기록·통계") { dismiss() }
            if stats.mornings == 0 && stats.adjustments == 0 && stats.snoozes == 0 {
                EmptyStateView(title: "아직 기록이 없어요", message: "기상 알람을 끄거나 교통 변화로 알람이 조정되면 여기에 쌓여요.", icon: "chart.bar.fill")
                    .padding(.top, Space.xl)
                Spacer()
            } else {
                ScrollView { content(stats) }
            }
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func content(_ stats: AlarmStats) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: 8.dp)
            Text("최근 30일 · 아침 \(stats.mornings)번").font(AppFont.labelMedium).foregroundStyle(AppColor.textSecondary)
            HStack(alignment: .lastTextBaseline, spacing: 10.dp) {
                Text(stats.onTimeRate < 0 ? "—" : "\(stats.onTimeRate)%").font(AppFont.displayLarge).foregroundStyle(AppColor.textPrimary)
                Text("제시간 기상 (5분 안에 끄기)").font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary)
            }
            Spacer().frame(height: Space.lg)
            HStack(spacing: 10.dp) {
                tile("평균 늦게 끈 시간", "\(stats.averageLateMinutes)분")
                tile("다시 울림", "\(stats.snoozes)번")
            }
            Spacer().frame(height: 10.dp)
            HStack(spacing: 10.dp) {
                tile("교통 변화로 조정", "\(stats.adjustments)번")
                tile("앞당긴 시간", "\(stats.adjustedEarlierMinutes)분")
            }
            Spacer().frame(height: Space.lg)
            SectionLabel(text: "최근 14일 기상")
            Spacer().frame(height: 10.dp)
            chart(stats.days)
                .padding(.horizontal, 14.dp).padding(.vertical, 16.dp)
                .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
            Spacer().frame(height: 10.dp)
            Text("막대는 기상 알람이 울린 뒤 끄기까지 걸린 시간이에요(30분에서 잘라 그려요). 5분 안에 끈 날은 회색, 더 늦게 끈 날은 강조색이에요.")
                .font(AppFont.bodySmall).foregroundStyle(AppColor.textTertiary)
        }
        .padding(.horizontal, Space.screen)
        .padding(.bottom, Space.xl)
    }

    private func tile(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4.dp) {
            Text(label).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
            Text(value).font(AppFont.headlineSmall).foregroundStyle(AppColor.textPrimary)
        }
        .padding(.horizontal, 16.dp).padding(.vertical, 14.dp)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
    }

    private func chart(_ days: [DayWake]) -> some View {
        HStack(alignment: .bottom, spacing: 0) {
            ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                VStack(spacing: 6.dp) {
                    ZStack(alignment: .bottom) {
                        Color.clear.frame(height: 96.dp)
                        if let late = day.lateMinutes?.intValue {
                            let fraction = max(CGFloat(min(max(late, 1), 30)) / 30, 0.08)
                            RoundedRectangle(cornerRadius: 6.dp).fill(late <= 5 ? AppColor.textTertiary : AppColor.accent)
                                .frame(width: 12.dp, height: 96.dp * fraction)
                        } else {
                            Circle().fill(AppColor.dotted).frame(width: 4.dp, height: 4.dp)
                        }
                    }
                    Text(day.weekday).font(AppFont.labelSmall).foregroundStyle(AppColor.textTertiary)
                }
                .frame(maxWidth: .infinity)
                .accessibilityLabel(day.lateMinutes.map { "\(day.month)월 \(day.day)일 \($0)분 늦게 끔" } ?? "\(day.month)월 \(day.day)일 알람 없음")
            }
        }
    }
}
