import SwiftUI
import UserNotifications

/// 홈(안드로이드 UI-B-001): 가장 가까운 일정의 기상 → 도착 노선도
struct HomeView: View {
    @EnvironmentObject private var store: AlarmStore
    @EnvironmentObject private var subscriptions: SubscriptionStore
    let onCreate: () -> Void
    let onOpen: (UUID) -> Void
    @State private var notificationsDenied = false

    var body: some View {
        VStack(spacing: 0) {
            if let alarm = store.nextAlarm ?? store.alarms.first {
                ScrollView { content(alarm) }
                    .refreshable { await store.refresh(alarm.id) }
                // 무료 플랜 안내 띠: 탭 바로 위에 고정
                if !subscriptions.isPro {
                    Button { subscriptions.paywallVisible = true } label: { ProStrip() }.buttonStyle(.plain)
                        .padding(.horizontal, Space.screen).padding(.top, 8.dp).padding(.bottom, 12.dp)
                }
            } else {
                ScreenHeader(title: "다음 알람")
                Spacer().frame(height: Space.xl)
                EmptyStateView(title: "아직 등록된 일정이 없어요", message: "도착지와 도착 시각만 알려주시면 알람을 계산해드려요.",
                               actionTitle: "새 일정 만들기", action: onCreate)
                Spacer()
            }
        }
        .background(AppColor.background)
        .task {
            let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
            notificationsDenied = status == .denied
        }
    }

    @ViewBuilder
    private func content(_ alarm: Alarm) -> some View {
        let plan = alarm.plan
        let upcoming = plan.map { $0.targetArrival > Date() } ?? false
        VStack(alignment: .leading, spacing: 0) {
            ScreenHeader(title: plan.map { upcoming ? TimeText.dayPartTitle($0.wakeUp) : "다음 알람" } ?? "다음 알람") {
                if let plan {
                    Button { Task { await store.refresh(alarm.id) } } label: {
                        if store.refreshing.contains(alarm.id) {
                            Pill(text: "확인 중…", foreground: AppColor.textPrimary)
                        } else {
                            StatusPill(plan: plan)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("교통 정보 새로고침")
                }
            }
            Text(summary(alarm, upcoming: upcoming))
                .font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary).lineLimit(1)
                .padding(.horizontal, Space.screen).padding(.top, 4.dp)

            VStack(spacing: 8.dp) {
                if notificationsDenied {
                    NoticeStrip(text: "알림과 정확한 알람을 허용해야 알람이 제때 울려요.", title: "권한 필요", icon: "bell.slash", danger: true,
                                actionTitle: "설정 열기") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                }
                if let plan, plan.isOffline {
                    NoticeStrip(text: "실시간 교통 정보를 가져오지 못했어요. 마지막 계산 결과를 기준으로 알람을 유지합니다.", icon: "icloud.slash")
                }
                if let weather = plan?.weather { WeatherStrip(weather: weather) }
                LateRiskCard(alarm: alarm)
                PendingAdjustmentCard(alarm: alarm)
                if !alarm.enabled {
                    NoticeStrip(text: "알람이 꺼져 있어요. 알람 탭에서 켜면 교통 정보를 다시 확인해요.")
                }
            }
            .padding(.horizontal, Space.screen).padding(.top, 16.dp)

            if let plan {
                Button { onOpen(alarm.id) } label: {
                    VStack(alignment: .leading, spacing: 0) {
                        RouteLineView(line: plan.routeLine(origin: alarm.origin.name, destination: alarm.destination.name))
                        Spacer().frame(height: 14.dp)
                        Text(UpdatedText.source(plan)).font(AppFont.bodySmall).foregroundStyle(AppColor.textTertiary)
                            .padding(.leading, 120.dp)
                    }
                    .padding(.horizontal, Space.screen).padding(.top, 12.dp).padding(.bottom, 8.dp)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.top, 12.dp)
            }
            Spacer().frame(height: Space.md)
        }
    }

    /// "11월 27일 (금) | 한성대학교 09:00 도착"
    private func summary(_ alarm: Alarm, upcoming: Bool) -> String {
        if let plan = alarm.plan, upcoming {
            return "\(TimeText.day(plan.targetArrival)) | \(alarm.destination.name) \(alarm.arrivalText) 도착"
        }
        return "\(alarm.destination.name) \(alarm.arrivalText) 도착"
    }
}

/// "✓ 교통 정상" / "⚠ 지연 +5분"(안드로이드 TrafficStatusChip)
struct StatusPill: View {
    let plan: SavedPlan
    var body: some View {
        let status = plan.status
        Pill(text: status.text, systemImage: status.icon,
             foreground: status.delayed ? AppColor.danger : AppColor.textPrimary,
             background: status.delayed ? AppColor.dangerContainer : AppColor.surfaceStrong)
    }
}

/// 화면 안 안내 띠(안드로이드 NoticeStrip): 아이콘 20dp + 문구, 모서리 18dp
struct NoticeStrip: View {
    let text: String
    var title: String? = nil
    var icon: String = "info.circle"
    var danger = false
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 12.dp) {
            Image(systemName: icon).font(.system(size: 17.dp)).foregroundStyle(danger ? AppColor.danger : AppColor.textSecondary)
                .frame(width: 20.dp)
            VStack(alignment: .leading, spacing: 2.dp) {
                if let title { Text(title).font(AppFont.labelLarge).foregroundStyle(danger ? AppColor.danger : AppColor.textPrimary) }
                Text(text).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle).font(AppFont.labelLarge).foregroundStyle(danger ? AppColor.danger : AppColor.accentText)
                        .padding(.horizontal, 12.dp).padding(.vertical, 10.dp)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.leading, 16.dp).padding(.trailing, actionTitle == nil ? 16.dp : 4.dp).padding(.vertical, 12.dp)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.field).fill(danger ? AppColor.dangerContainer : AppColor.surface))
    }
}

/// 날씨 띠(안드로이드 WeatherStrip): 비·눈이면 여유를 더 뒀다고, 강수확률이 높으면 우산을 챙기라고
struct WeatherStrip: View {
    let weather: WeatherNote
    var body: some View {
        if weather.extraMinutes > 0 || weather.umbrella {
            let label = WeatherText.kind(weather.precipitation)
            NoticeStrip(
                text: weather.extraMinutes > 0
                    ? "\(label) 예보 \(weather.probability)% · 여유 \(weather.extraMinutes)분 더 뒀어요 · 우산 챙기세요"
                    : "\(label) 예보 \(weather.probability)% · 우산 챙기세요",
                icon: weather.precipitation == "SNOW" || weather.precipitation == "RAIN_SNOW" ? "snowflake" : "umbrella"
            )
        }
    }
}

/// 무료 플랜 안내 띠(안드로이드 ProStrip)
struct ProStrip: View {
    var body: some View {
        HStack(spacing: 14.dp) {
            TagBadge(text: "무료", foreground: AppColor.textPrimary, background: AppColor.background)
                .overlay(Capsule().strokeBorder(AppColor.outline, lineWidth: 1.dp))
            VStack(alignment: .leading, spacing: 0) {
                Text("출발 전 교통 확인 1회").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary).lineLimit(1)
                Text("PRO는 출발 직전까지 실시간 재계산").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(1)
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.system(size: 16.dp, weight: .semibold)).foregroundStyle(AppColor.textPrimary)
        }
        .padding(.horizontal, 16.dp).padding(.vertical, 14.dp)
        .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
    }
}

/// 빈 화면(안드로이드 EmptyState): 72dp 원 안의 알람 아이콘, 제목, 설명, 버튼
struct EmptyStateView: View {
    let title: String
    let message: String
    var icon = "alarm"
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            Image(systemName: icon).font(.system(size: 28.dp)).foregroundStyle(AppColor.textPrimary)
                .frame(width: 72.dp, height: 72.dp).background(Circle().fill(AppColor.surface))
            Spacer().frame(height: Space.md)
            Text(title).font(AppFont.titleLarge).foregroundStyle(AppColor.textPrimary).multilineTextAlignment(.center)
            Spacer().frame(height: Space.sm)
            Text(message).font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary).multilineTextAlignment(.center)
            if let actionTitle, let action {
                Spacer().frame(height: Space.lg)
                PrimaryButton(title: actionTitle, action: action)
            }
        }
        .padding(.horizontal, Space.screen).padding(.vertical, Space.xl)
    }
}

enum UpdatedText {
    /// "실시간 교통 정보 · 4분 전 갱신" / "마지막 계산 기준 · 방금 갱신"
    static func source(_ plan: SavedPlan) -> String {
        "\(plan.isOffline ? "마지막 계산 기준" : "실시간 교통 정보") · \(ago(plan.calculatedAt))"
    }

    /// 안드로이드 relativeUpdateText
    static func ago(_ date: Date) -> String {
        let minutes = Int(Date().timeIntervalSince(date) / 60)
        if minutes < 1 { return "방금 갱신" }
        if minutes < 60 { return "\(minutes)분 전 갱신" }
        if minutes < 24 * 60 { return "\(minutes / 60)시간 전 갱신" }
        return "\(TimeText.monthDay(date)) 갱신"
    }
}
