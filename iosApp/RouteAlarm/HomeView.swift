import SwiftUI

/// 홈: 가장 가까운 알람의 노선도(안드로이드 홈과 같은 구성)
struct HomeView: View {
    @EnvironmentObject private var store: AlarmStore
    let onCreate: () -> Void

    var body: some View {
        ScrollView {
            if let alarm = store.nextAlarm, let plan = alarm.plan {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(TimeText.relativeMorning(plan.wakeUp)).font(AppFont.headline).foregroundStyle(AppColor.textPrimary)
                            Text("\(TimeText.day(plan.targetArrival)) | \(alarm.destination.name) \(alarm.arrivalText) 도착")
                                .font(AppFont.body).foregroundStyle(AppColor.textSecondary)
                        }
                        Spacer()
                        StatusPill(plan: plan)
                    }
                    .padding(.bottom, 24)

                    if let weather = plan.weather, weather.extraMinutes > 0 || weather.umbrella {
                        WeatherBanner(weather: weather).padding(.bottom, 16)
                    }

                    RouteLineView(line: plan.routeLine(origin: alarm.origin.name, destination: alarm.destination.name))

                    Text(UpdatedText.of(plan))
                        .font(AppFont.caption).foregroundStyle(AppColor.textTertiary)
                        .padding(.leading, 118).padding(.top, 10)

                    Button { Task { await store.refresh(alarm.id) } } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("지금 교통 정보로 다시 계산").font(.app(17, .bold)).foregroundStyle(AppColor.textPrimary)
                                Text("공용 계산 엔진(Kotlin)으로 안드로이드와 같은 시각을 계산해요")
                                    .font(AppFont.caption).foregroundStyle(AppColor.textSecondary)
                            }
                            Spacer()
                            if store.refreshing.contains(alarm.id) { ProgressView() } else { Image(systemName: "arrow.clockwise") }
                        }
                        .padding(20)
                        .background(RoundedRectangle(cornerRadius: 24).fill(AppColor.surface))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 28)
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
            } else {
                EmptyAlarms(onCreate: onCreate).padding(.top, 120)
            }
        }
        .refreshable { await store.refreshAll() }
        .background(AppColor.background)
    }
}

/// "✓ 교통 정상" / "지연 5분"
struct StatusPill: View {
    let plan: SavedPlan
    var body: some View {
        let status = plan.status
        Pill(
            text: status.text, systemImage: status.icon,
            foreground: status.delayed ? AppColor.danger : AppColor.textPrimary,
            background: status.delayed ? AppColor.dangerContainer : AppColor.surfaceStrong
        )
    }
}

struct WeatherBanner: View {
    let weather: WeatherNote
    var body: some View {
        Label(WeatherText.label(weather), systemImage: WeatherText.symbol(weather.precipitation))
            .font(AppFont.label)
            .foregroundStyle(Color(hex: 0x1D4ED8))
            .padding(.horizontal, 16).padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(hex: 0x2563EB).opacity(0.1)))
    }
}

struct EmptyAlarms: View {
    let onCreate: () -> Void
    var body: some View {
        VStack(spacing: 14) {
            AlarmDot(size: 64)
            Text("알람이 없어요").font(AppFont.title).foregroundStyle(AppColor.textPrimary)
            Text("몇 시까지 도착할지 알려 주면\n교통 상황으로 일어날 시각을 계산해요.")
                .font(AppFont.body).foregroundStyle(AppColor.textSecondary).multilineTextAlignment(.center)
            PrimaryButton(title: "알람 만들기", action: onCreate).frame(width: 220).padding(.top, 8)
        }
        .frame(maxWidth: .infinity)
    }
}

enum UpdatedText {
    /// "실시간 교통 정보 · 4분 전 갱신"
    static func of(_ plan: SavedPlan) -> String {
        let source = plan.usedFallback ? "교통 정보를 받지 못해 기본 60분으로 계산" : (plan.isRealtime ? "실시간 교통 정보" : "최근 교통 정보")
        return "\(source) · \(ago(plan.calculatedAt))"
    }

    static func ago(_ date: Date) -> String {
        let minutes = Int(Date().timeIntervalSince(date) / 60)
        if minutes < 1 { return "방금 갱신" }
        if minutes < 60 { return "\(minutes)분 전 갱신" }
        return "\(minutes / 60)시간 전 갱신"
    }
}
