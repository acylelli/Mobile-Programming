import SwiftUI
import Shared

/* ───────────── PRO 표시 ───────────── */

/// 검정 바탕 PRO(잠긴 항목, 일정 한도 카드는 흐리게)
struct ProBadge: View {
    var dim = false
    var body: some View {
        Pill(text: "PRO", foreground: dim ? AppColor.textTertiary : AppColor.background, background: AppColor.textPrimary, font: AppFont.labelSmall)
    }
}

/// 강조색 바탕 PRO(검정 배너 위, 현재 플랜 카드)
struct ProAccentBadge: View {
    var body: some View {
        Pill(text: "PRO", foreground: AppColor.onAccent, background: AppColor.accent, font: AppFont.labelMedium)
    }
}

/// 개인/단체 같은 두 칸 고르기(안드로이드 SegmentedControl): 높이 48dp, 안쪽 4dp, 고른 칸은 흰 바탕
struct SegmentedControl: View {
    let options: [String]
    @Binding var selection: Int

    var body: some View {
        HStack(spacing: 4.dp) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, label in
                let selected = index == selection
                Button { selection = index } label: {
                    Text(label).font(selected ? AppFont.labelLarge : AppFont.bodyMedium)
                        .foregroundStyle(selected ? AppColor.textPrimary : AppColor.textSecondary).lineLimit(1)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(RoundedRectangle(cornerRadius: 12.dp).fill(selected ? AppColor.background : .clear)
                            .shadow(color: .black.opacity(selected ? 0.12 : 0), radius: 1.dp, y: 0.5.dp))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(4.dp)
        .frame(height: 48.dp)
        .background(RoundedRectangle(cornerRadius: 16.dp).fill(AppColor.surfaceStrong))
    }
}

/// 설정 맨 위 PRO 배너(무료 플랜에만)
struct ProBanner: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 0) {
                ProAccentBadge()
                Spacer().frame(width: 14.dp)
                VStack(alignment: .leading, spacing: 0) {
                    Text("PRO 이용권").font(AppTextStyle(size: 18, weight: .bold, lineHeight: 24)).foregroundStyle(AppColor.onButton)
                    Text("실시간 재계산, 일정 무제한, 시간표 연동").font(AppFont.bodySmall).foregroundStyle(AppColor.onButton.opacity(0.72)).lineLimit(1)
                }
                Spacer(minLength: 10.dp)
                Image(systemName: "chevron.right").font(.system(size: 14.dp, weight: .bold)).foregroundStyle(AppColor.onAccent)
                    .frame(width: 32.dp, height: 32.dp).background(Circle().fill(AppColor.accent))
            }
            .padding(.leading, 18.dp).padding(.trailing, 16.dp).padding(.vertical, 16.dp)
            .background(RoundedRectangle(cornerRadius: Radius.card).fill(AppColor.button))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// 알람 목록 아래 무료 플랜 한도 카드(점선 테두리)
struct PlanLimitCard: View {
    let used: Int
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12.dp) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("무료 플랜 일정 \(used) / \(PlanLimits.shared.FREE_SCHEDULE_LIMIT) 사용 중").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                    Text("PRO는 일정을 무제한으로 만들 수 있어요").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                }
                Spacer(minLength: 0)
                ProBadge(dim: true)
            }
            .padding(.horizontal, 20.dp).padding(.vertical, 16.dp)
            .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(AppColor.dotted, style: StrokeStyle(lineWidth: 1.5.dp, dash: [5.dp, 4.dp])))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/* ───────────── 페이월 ───────────── */

/// PRO 페이월(안드로이드 UI-E-001): 잠긴 기능을 눌렀을 때만 뜨는 시트
struct PaywallSheet: View {
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let onOpenGroupPass: () -> Void
    @State private var tab = 0
    @State private var option: SubscriptionProduct? = .yearly
    @State private var processing = false
    @State private var error: String?
    @State private var toast: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("PRO로 더 정확하게").font(AppFont.headlineMedium).foregroundStyle(AppColor.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Spacer().frame(height: 14.dp)
                SegmentedControl(options: ["개인", "단체 (학교, 기업)"], selection: $tab)
                Spacer().frame(height: 14.dp)
                if tab == 0 { personal } else { group }
            }
            .padding(.horizontal, Space.screen).padding(.top, 28.dp).padding(.bottom, 12.dp)
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(tab == 0 ? 610.dp : 470.dp)])
        .presentationDragIndicator(.visible)
        .presentationBackground(AppColor.background)
        .overlay(alignment: .bottom) { if let toast { Snackbar(text: toast) { self.toast = nil } } }
    }

    @ViewBuilder private var personal: some View {
        VStack(spacing: 10.dp) {
            planCard("연간", "월 \(amountText(PlanLimits.shared.YEARLY_MONTHLY_EQUIVALENT_WON))원꼴, \(PlanLimits.shared.YEARLY_DISCOUNT_PERCENT)% 할인",
                     price: wonText(Int(SubscriptionProduct.yearly.priceWon)), recommended: true, product: .yearly)
            planCard("월간", "30일 이용", price: wonText(Int(SubscriptionProduct.monthly.priceWon)), product: .monthly)
            planCard("무료로 계속", "일정 \(PlanLimits.shared.FREE_SCHEDULE_LIMIT)개, 광고 포함", price: wonText(0), product: nil)
        }
        Spacer().frame(height: 14.dp)
        let benefits = ["실시간 재계산", "일정 무제한", "시간표, 캘린더 연동", "광고 제거", "지각 위험 시 택시 안내"]
        VStack(alignment: .leading, spacing: 6.dp) {
            ForEach(0..<3, id: \.self) { row in
                HStack(spacing: 0) {
                    benefit(benefits[row * 2]).frame(maxWidth: .infinity, alignment: .leading)
                    benefit(row * 2 + 1 < benefits.count ? benefits[row * 2 + 1] : "").frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        if let error {
            Spacer().frame(height: 10.dp)
            Text(error).font(AppFont.bodySmall).foregroundStyle(AppColor.danger)
        }
        Spacer().frame(height: 14.dp)
        PrimaryButton(title: option == nil ? "무료로 계속하기" : (processing ? "결제창을 여는 중…" : "\(amountText(option!.priceWon))원 결제하기"), enabled: !processing) {
            guard let product = option else { dismiss(); return }
            processing = true
            error = nil
            Task {
                if let failure = await subscriptions.startCheckout(product) {
                    error = failure
                } else {
                    subscriptions.paywallVisible = false
                }
                processing = false
            }
        }
        Spacer().frame(height: 10.dp)
        footnote(option.map { "결제하면 \($0.days)일 동안 PRO | 자동 갱신 없음" } ?? "무료 플랜은 일정 2개와 출발 전 교통 확인 1회를 제공해요")
        if option != nil {
            Spacer().frame(height: 4.dp)
            footnote("토스페이먼츠 테스트 결제라 실제로 청구되지 않아요.")
        }
    }

    @ViewBuilder private var group: some View {
        Text("학교, 기업이 구성원 수만큼 일괄 구매해 구성원에게 PRO를 제공하는 이용권이에요.").font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary)
        Spacer().frame(height: 14.dp)
        VStack(alignment: .leading, spacing: 10.dp) {
            benefit("구성원 모두 PRO 전 기능 이용")
            benefit("인원과 기간을 정해 일괄 구매")
            benefit("기관에는 이용 인원만 집계")
        }
        .padding(16.dp)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
        Spacer().frame(height: 18.dp)
        PrimaryButton(title: "단체 이용권 코드 입력") { onOpenGroupPass() }
        Spacer().frame(height: 10.dp)
        SecondaryButton(title: "도입 문의하기") {
            // 받는 사람은 비워 둔 채 메일 앱의 작성 화면만 연다.
            let subject = "RouteAlarm 단체 이용권 도입 문의".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            if let url = URL(string: "mailto:?subject=\(subject)"), UIApplication.shared.canOpenURL(url) {
                openURL(url)
            } else {
                toast = "메일 앱을 찾지 못했어요."
            }
        }
        Spacer().frame(height: 8.dp)
    }

    private func planCard(_ title: String, _ body: String, price: String, recommended: Bool = false, product: SubscriptionProduct?) -> some View {
        let selected = option == product
        return Button {
            option = product
            error = nil
        } label: {
            HStack(spacing: 0) {
                RadioDot(selected: selected)
                Spacer().frame(width: 14.dp)
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 8.dp) {
                        Text(title).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                        if recommended { TagBadge(text: "추천", foreground: AppColor.onAccent, background: AppColor.accent) }
                    }
                    Text(body).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(1)
                }
                Spacer(minLength: 8.dp)
                Text(price).font(AppFont.figure).monospacedDigit().foregroundStyle(AppColor.textPrimary)
            }
            .padding(.leading, 16.dp).padding(.trailing, 18.dp).padding(.vertical, 13.dp)
            .background(RoundedRectangle(cornerRadius: Radius.group).fill(selected ? AppColor.accentContainer : AppColor.background))
            .overlay(RoundedRectangle(cornerRadius: Radius.group).strokeBorder(selected ? AppColor.accent : AppColor.outline, lineWidth: selected ? 1.5.dp : 1.dp))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func benefit(_ text: String) -> some View {
        HStack(spacing: 6.dp) {
            if !text.isEmpty {
                Image(systemName: "checkmark").font(.system(size: 13.dp, weight: .bold)).foregroundStyle(AppColor.accentText).frame(width: 18.dp, height: 18.dp)
                Text(text).font(AppFont.bodyMedium).foregroundStyle(AppColor.textPrimary).lineLimit(1)
            }
        }
    }

    private func footnote(_ text: String) -> some View {
        Text(text).font(AppTextStyle(size: 12.1, weight: .regular, lineHeight: 19)).foregroundStyle(AppColor.textTertiary)
            .multilineTextAlignment(.center).frame(maxWidth: .infinity)
    }
}

/* ───────────── 구독 관리 ───────────── */

/// 구독 관리(안드로이드 UI-F-003): 현재 플랜, 구독 정보, 결제 내역
struct SubscriptionView: View {
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let entitlement = subscriptions.entitlement
        let isPro = subscriptions.isPro
        VStack(spacing: 0) {
            BackHeader(title: "구독 관리") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Spacer().frame(height: 8.dp)
                    SectionLabel(text: "현재 플랜")
                    Spacer().frame(height: 10.dp)
                    currentPlan(entitlement, isPro: isPro)
                    if isPro {
                        Spacer().frame(height: Space.lg)
                        SectionLabel(text: "구독 정보")
                        Spacer().frame(height: 10.dp)
                        VStack(alignment: .leading, spacing: 12.dp) {
                            if let organization = entitlement.organization { detail("기관", organization) }
                            if let started = entitlement.startedAtMillis { detail("시작일", dotted(started.int64Value)) }
                            if entitlement.isPersonalSubscription {
                                if let expires = entitlement.expiresAtMillis {
                                    detail(entitlement.isInTrial(nowMillis: subscriptions.nowMillis) ? "첫 결제일" : entitlement.autoRenew ? "다음 결제일" : "이용 종료일",
                                           dotted(expires.int64Value))
                                }
                                detail("결제 금액", priceText(entitlement))
                                detail("자동 갱신", entitlement.autoRenew ? "사용 중" : "해지됨")
                            } else if let expires = entitlement.expiresAtMillis {
                                // 이용 종료 시각은 마지막 날의 자정이므로 하루 전 날짜를 보여준다.
                                detail("이용 종료일", dotted(expires.int64Value - 1000))
                            }
                        }
                        .padding(.horizontal, 18.dp).padding(.vertical, 16.dp)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
                    }
                    Spacer().frame(height: Space.lg)
                    SectionLabel(text: "결제 내역")
                    Spacer().frame(height: 10.dp)
                    history(entitlement, isPro: isPro)
                    Spacer().frame(height: Space.lg)
                    if !isPro {
                        NavigationLink { GroupPassView() } label: { SecondaryButtonLabel(title: "단체 이용권 코드 입력") }
                            .buttonStyle(.plain)
                    }
                    if isPro {
                        // 테스트 결제·테스트 이용권을 다시 해 볼 수 있도록 무료 플랜으로 되돌린다.
                        Spacer().frame(height: 8.dp)
                        TextLink(text: "테스트: 무료 플랜으로 되돌리기") { subscriptions.resetToFree() }.frame(maxWidth: .infinity)
                    }
                    Spacer().frame(height: Space.lg)
                }
                .padding(.horizontal, Space.screen)
            }
            if !isPro {
                PrimaryButton(title: "PRO 이용권 구매") { subscriptions.paywallVisible = true }
                    .padding(.horizontal, Space.screen).padding(.top, 8.dp).padding(.bottom, 16.dp)
            }
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func currentPlan(_ entitlement: Entitlement, isPro: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10.dp) {
                Text(subscriptions.planName).font(AppFont.headlineSmall).foregroundStyle(AppColor.textPrimary).accessibilityAddTraits(.isHeader)
                if entitlement.isInTrial(nowMillis: subscriptions.nowMillis) {
                    TagBadge(text: "무료체험 중", foreground: AppColor.onAccent, background: AppColor.accent)
                } else if isPro {
                    ProAccentBadge()
                }
            }
            Spacer().frame(height: 6.dp)
            Text(!isPro ? "일정 \(PlanLimits.shared.FREE_SCHEDULE_LIMIT)개, 출발 전 교통 확인 1회"
                 : entitlement.plan == .groupPass ? "\(entitlement.organization ?? "")에서 제공하는 PRO 이용권" : "실시간 재계산, 일정 무제한, 광고 제거")
                .font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
        }
        .padding(.horizontal, 20.dp).padding(.vertical, 18.dp)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.card).fill(isPro ? AppColor.accentContainer : AppColor.background))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(isPro ? AppColor.accent : AppColor.outline, lineWidth: isPro ? 1.5.dp : 1.dp))
    }

    @ViewBuilder private func history(_ entitlement: Entitlement, isPro: Bool) -> some View {
        if !isPro || entitlement.startedAtMillis == nil {
            Text("결제 내역이 없어요").font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
        } else {
            let started = dotted(entitlement.startedAtMillis!.int64Value)
            VStack(alignment: .leading, spacing: 12.dp) {
                if entitlement.plan == .groupPass {
                    historyRow(started, "단체 이용권 적용", "기관 부담")
                } else {
                    historyRow(started, "7일 무료체험 시작", wonText(0))
                    if entitlement.autoRenew, let next = entitlement.expiresAtMillis {
                        Rectangle().fill(AppColor.outline).frame(height: 1.dp)
                        historyRow(dotted(next.int64Value), "결제 예정", priceText(entitlement), muted: true)
                    }
                }
            }
            .padding(.horizontal, 18.dp).padding(.vertical, 16.dp)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
        }
    }

    private func historyRow(_ date: String, _ title: String, _ amount: String, muted: Bool = false) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(AppFont.bodyLarge).foregroundStyle(muted ? AppColor.textSecondary : AppColor.textPrimary)
                Text(date).font(AppFont.bodySmall).foregroundStyle(AppColor.textTertiary)
            }
            Spacer()
            Text(amount).font(AppFont.titleSmall).foregroundStyle(muted ? AppColor.textSecondary : AppColor.textPrimary)
        }
    }

    private func priceText(_ entitlement: Entitlement) -> String {
        switch entitlement.plan {
        case .proYearly: return "연 \(amountText(SubscriptionProduct.yearly.priceWon))원"
        case .proMonthly: return "월 \(amountText(SubscriptionProduct.monthly.priceWon))원"
        default: return wonText(0)
        }
    }
}

/// 라벨 · 값 한 줄(구독 정보, 확인된 이용권)
func detail(_ label: String, _ value: String) -> some View {
    HStack(spacing: 12.dp) {
        Text(label).font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
        Text(value).font(AppFont.titleSmall).foregroundStyle(AppColor.textPrimary).frame(maxWidth: .infinity, alignment: .trailing)
    }
}

/// "2026.11.26"
func dotted(_ millis: Int64) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "ko_KR")
    formatter.dateFormat = "yyyy.MM.dd"
    return formatter.string(from: Date(millis: millis))
}

/// NavigationLink 안에 쓰는 보조 버튼 모양
struct SecondaryButtonLabel: View {
    let title: String
    var body: some View {
        Text(title).font(AppFont.button).foregroundStyle(AppColor.textPrimary)
            .frame(maxWidth: .infinity, minHeight: 52.dp)
            .background(RoundedRectangle(cornerRadius: Radius.button).fill(AppColor.background))
            .overlay(RoundedRectangle(cornerRadius: Radius.button).strokeBorder(AppColor.outline, lineWidth: 1.dp))
            .contentShape(Rectangle())
    }
}

/* ───────────── 단체 이용권 ───────────── */

/// 단체 이용권 코드 입력(안드로이드 UI-E-002)
struct GroupPassView: View {
    @EnvironmentObject private var subscriptions: SubscriptionStore
    @Environment(\.dismiss) private var dismiss
    @State private var code = ""
    @State private var check: Check = .idle
    @State private var applying = false
    @State private var duplicateOrganization: String?
    @FocusState private var focused: Bool

    enum Check: Equatable { case idle, checking, verified(GroupPass), failed(String) }

    private let codeStyle = AppTextStyle(size: 24, weight: .heavy, lineHeight: 27, tracking: 1)

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "단체 이용권") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Spacer().frame(height: 12.dp)
                    Text("기관에서 받은\n코드를 입력하세요").font(AppTextStyle(size: 28, weight: .heavy, lineHeight: 37, tracking: -0.5))
                        .foregroundStyle(AppColor.textPrimary).accessibilityAddTraits(.isHeader)
                    Spacer().frame(height: 10.dp)
                    Text("학교, 회사가 구성원에게 제공하는 PRO 이용권이에요.").font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
                    Spacer().frame(height: 20.dp)
                    codeField
                    Spacer().frame(height: 8.dp)
                    Text("테스트 코드: HSU-2026-WAKE7").font(AppFont.bodySmall).foregroundStyle(AppColor.textTertiary)
                    Spacer().frame(height: 16.dp)
                    switch check {
                    case .idle: EmptyView()
                    case .checking:
                        HStack(spacing: 10.dp) {
                            ProgressView().controlSize(.small).tint(AppColor.accent)
                            Text("코드를 확인하고 있어요…").font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
                        }
                    case .verified(let pass): verifiedCard(pass)
                    case .failed(let message): NoticeStrip(text: message, icon: "exclamationmark.circle", danger: true)
                    }
                    Spacer().frame(height: 18.dp)
                    infoLine("lock", "기관에는 이용 인원만 집계되며, 개인 위치와 일정 정보는 제공되지 않아요.")
                    Spacer().frame(height: 10.dp)
                    infoLine("creditcard", "개인 구독 중이라면 다음 결제일부터 자동 갱신이 해지되도록 안내해 드려요.")
                    Spacer().frame(height: Space.lg)
                }
                .padding(.horizontal, Space.screen)
            }
            .scrollDismissesKeyboard(.interactively)
            PrimaryButton(title: applying ? "적용하는 중…" : "이용권 적용하기", enabled: isVerified && !applying) { apply() }
                .padding(.horizontal, Space.screen).padding(.top, 8.dp).padding(.bottom, 16.dp)
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
        .task(id: code) {
            // 입력이 멈추면(0.4초) 완성된 코드만 서버에 확인한다.
            let normalized = GroupPassCode.shared.normalize(raw: code)
            guard GroupPassCode.shared.isComplete(code: normalized) else { check = .idle; return }
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            check = .checking
            let outcome = await subscriptions.verifyGroupPass(normalized)
            guard !Task.isCancelled else { return }
            if let verified = outcome as? GroupPassOutcome.Verified {
                check = .verified(verified.pass)
            } else {
                check = .failed(((outcome as? GroupPassOutcome.Failed)?.error ?? .server).message)
            }
        }
        .alert("개인 구독 자동 갱신을 해지해 주세요", isPresented: Binding(get: { duplicateOrganization != nil }, set: { if !$0 { finish() } })) {
            Button("App Store에서 구독 관리") {
                if let url = URL(string: "https://apps.apple.com/account/subscriptions") { UIApplication.shared.open(url) }
                finish()
            }
            Button("나중에", role: .cancel) { finish() }
        } message: {
            Text("단체 이용권으로 PRO를 쓸 수 있어요. 개인 구독이 이중으로 결제되지 않도록 App Store에서 자동 갱신을 해지해 주세요.")
        }
    }

    private var isVerified: Bool { if case .verified = check { return true } else { return false } }

    /// 굵은 테두리 안의 큰 글자. 마지막 하이픈 앞은 옅게 칠해 입력 중인 묶음이 눈에 들어오게 한다(HSU-2026-WAKE7).
    private var codeField: some View {
        ZStack(alignment: .leading) {
            if code.isEmpty {
                Text("HSU-2026-WAKE7").font(codeStyle).foregroundStyle(AppColor.textTertiary.opacity(0.6)).lineLimit(1)
            }
            emphasized.font(codeStyle).lineLimit(1).allowsHitTesting(false)
            TextField("", text: Binding(get: { code }, set: { code = GroupPassCode.shared.sanitize(raw: $0).uppercased() }))
                .font(codeStyle).foregroundStyle(.clear).tint(AppColor.accent)
                .textInputAutocapitalization(.characters).autocorrectionDisabled().keyboardType(.asciiCapable)
                .submitLabel(.done).focused($focused)
                .accessibilityLabel("단체 이용권 코드")
        }
        .padding(.horizontal, 20.dp)
        .frame(height: 66.dp)
        .overlay(RoundedRectangle(cornerRadius: Radius.group).strokeBorder(AppColor.textPrimary, lineWidth: 2.dp))
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
    }

    private var emphasized: Text {
        let shown = code.uppercased()
        let split = shown.lastIndex(of: "-").map { shown.index(after: $0) } ?? shown.startIndex
        return Text(shown[..<split]).foregroundColor(AppColor.textTertiary) + Text(shown[split...]).foregroundColor(AppColor.textPrimary)
    }

    private func verifiedCard(_ pass: GroupPass) -> some View {
        VStack(alignment: .leading, spacing: 10.dp) {
            HStack(spacing: 6.dp) {
                Image(systemName: "checkmark").font(.system(size: 15.dp, weight: .bold)).foregroundStyle(AppColor.textPrimary).frame(width: 20.dp, height: 20.dp)
                Text("확인된 이용권").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
            }
            detail("기관", pass.organization)
            detail("이용 기간", pass.periodText)
            detail("적용 플랜", "PRO (전 기능)")
        }
        .padding(.horizontal, 18.dp).padding(.vertical, 16.dp)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.card).fill(AppColor.surface))
    }

    private func infoLine(_ icon: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10.dp) {
            Image(systemName: icon).font(.system(size: 14.dp)).foregroundStyle(AppColor.textSecondary).frame(width: 17.dp, height: 17.dp).padding(.top, 1.dp)
            Text(text).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
        }
    }

    private func apply() {
        applying = true
        Task {
            let result = await subscriptions.applyGroupPass(code)
            applying = false
            if let organization = result.organization {
                subscriptions.message = "\(organization) 단체 이용권을 적용했어요."
                if result.hadPersonal { duplicateOrganization = organization } else { dismiss() }
            } else if let error = result.error {
                check = .failed(error)
            }
        }
    }

    private func finish() {
        duplicateOrganization = nil
        dismiss()
    }
}
