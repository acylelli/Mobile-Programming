import Foundation
import SafariServices
import Shared
import SwiftUI

/// PRO 구독 상태(안드로이드 DefaultSubscriptionRepository 와 같은 규칙, 판단은 공용 SubscriptionService)
/// - 서버가 승인한 결과만 저장한다. 네트워크 오류면 저장된 값을 그대로 둔다.
/// - 카드 정보나 영수증 원문은 저장하지 않는다. 플랜 종류와 기간만 남긴다.
@MainActor
final class SubscriptionStore: ObservableObject {
    static let shared = SubscriptionStore()

    @Published private(set) var entitlement: Entitlement = Entitlement.companion.Free
    /// 앱 어디서든 PRO 페이월을 띄운다(안드로이드 showPaywall)
    @Published var paywallVisible = false
    /// 결제창(사파리 시트)
    @Published var checkoutURL: URL?
    /// 결제·이용권 결과 안내(스낵바)
    @Published var message: String?

    private let service = SubscriptionService(client: ProxyClient(baseUrl: ProxyClient.companion.DEFAULT_BASE_URL, client: nil))
    private let defaults = UserDefaults.standard
    private var checking = false

    private enum Key {
        static let entitlement = "entitlement.v1"
        static let pendingOrderId = "payment.pendingOrderId"
        static let pendingOrderAt = "payment.pendingOrderAt"
        static let paywallPromptDay = "paywall.lastPromptDay"
    }

    private init() { entitlement = Self.load() }

    var nowMillis: Int64 { Date().millis }
    var isPro: Bool { entitlement.isPro(nowMillis: nowMillis) }
    var planName: String { entitlement.planName(nowMillis: nowMillis) }

    // MARK: 결제(토스페이먼츠 테스트 결제창)

    /// 서버에 주문을 만들고 결제창을 연다. 주문 번호를 먼저 기억해 두면 앱으로 돌아오지 못해도 결과를 확인할 수 있다.
    func startCheckout(_ product: SubscriptionProduct) async -> String? {
        let outcome = try? await service.startCheckout(product: product, platform: "ios")
        guard let ready = outcome as? CheckoutOutcome.Ready, let url = URL(string: ready.checkoutUrl) else {
            return ((outcome as? CheckoutOutcome.Failed)?.error ?? .purchaseFailed).message
        }
        defaults.set(ready.orderId, forKey: Key.pendingOrderId)
        defaults.set(Double(nowMillis), forKey: Key.pendingOrderAt)
        checkoutURL = url
        return nil
    }

    /// 결제창에서 routealarm://payment 로 돌아왔다. 링크의 result 값은 믿지 않고 서버가 승인한 결과만 확인한다.
    func handle(url: URL) {
        guard url.scheme == "routealarm", url.host == "payment",
              let orderId = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "orderId" })?.value
        else { return }
        checkoutURL = nil
        Task { await complete(orderId: orderId, announce: true) }
    }

    /// 결제창을 그냥 닫았거나 앱을 다시 열었을 때: 기억해 둔 주문이 있으면 결과를 확인한다.
    func resumePending(announceFailure: Bool = false) async {
        guard let orderId = pendingOrderId() else { return }
        await complete(orderId: orderId, announce: announceFailure)
    }

    private func complete(orderId: String, announce: Bool) async {
        guard !checking else { return }
        checking = true
        defer { checking = false }
        if announce { message = "결제 결과를 확인하고 있어요…" }
        let outcome = try? await service.completePayment(orderId: orderId, nowMillis: nowMillis)
        switch outcome {
        case let paid as PaymentOutcome.Paid:
            save(paid.entitlement)
            clearPendingOrder(orderId)
            AlarmStore.shared.planChanged()
            message = "PRO가 적용됐어요. (테스트 결제)"
        case is PaymentOutcome.Declined:
            clearPendingOrder(orderId)
            if announce { message = "결제가 완료되지 않아 플랜을 바꾸지 않았어요." }
        default:
            // 결제 중이거나 확인하지 못했으면 주문을 남겨 두고 나중에 다시 확인한다.
            if announce { message = "결제가 완료되지 않아 플랜을 바꾸지 않았어요." }
        }
    }

    private func pendingOrderId() -> String? {
        guard let id = defaults.string(forKey: Key.pendingOrderId) else { return nil }
        let at = Int64(defaults.double(forKey: Key.pendingOrderAt))
        // 서버는 주문을 하루만 보관한다. 그보다 오래된 주문은 더 확인하지 않는다.
        if at == 0 || at + SubscriptionService.companion.PENDING_ORDER_TTL_MILLIS < nowMillis {
            clearPendingOrder(id)
            return nil
        }
        return id
    }

    private func clearPendingOrder(_ orderId: String) {
        if defaults.string(forKey: Key.pendingOrderId) == orderId { defaults.removeObject(forKey: Key.pendingOrderId) }
    }

    // MARK: 단체 이용권

    func verifyGroupPass(_ code: String) async -> GroupPassOutcome? {
        try? await service.verifyGroupPass(rawCode: code, nowMillis: nowMillis)
    }

    /// 적용하면 기관 이름을, 실패하면 nil 과 오류 문구를 돌려준다. hadPersonal: 자동 갱신 개인 구독 중이었는지
    func applyGroupPass(_ code: String) async -> (organization: String?, error: String?, hadPersonal: Bool) {
        let previous = entitlement
        let hadPersonal = previous.isPersonalSubscription && previous.autoRenew && previous.isPro(nowMillis: nowMillis)
        let outcome = try? await service.redeemGroupPass(rawCode: code, nowMillis: nowMillis)
        if let applied = outcome as? GroupPassOutcome.Applied {
            save(applied.entitlement)
            AlarmStore.shared.planChanged()
            return (applied.entitlement.organization ?? "", nil, hadPersonal)
        }
        return (nil, ((outcome as? GroupPassOutcome.Failed)?.error ?? .server).message, false)
    }

    /// 무료 플랜으로 되돌린다(테스트 결제·이용권을 다시 해 볼 수 있게). 일정은 그대로 둔다.
    func resetToFree() {
        defaults.removeObject(forKey: Key.entitlement)
        entitlement = Entitlement.companion.Free
        AlarmStore.shared.planChanged()
    }

    // MARK: 일정 한도

    /// 새 일정(+): 무료 플랜 한도에 차 있으면 하루 한 번 페이월, 그다음부터는 안내 문구(안드로이드 AlarmListViewModel)
    enum AddDecision { case create, paywall, limitMessage }

    func addDecision(scheduleCount: Int) -> AddDecision {
        guard PlanLimits.shared.reached(used: Int32(scheduleCount), isPro: isPro) else { return .create }
        let today = Calendar.current.ordinality(of: .day, in: .era, for: Date()) ?? 0
        if defaults.integer(forKey: Key.paywallPromptDay) == today { return .limitMessage }
        defaults.set(today, forKey: Key.paywallPromptDay)
        return .paywall
    }

    // MARK: 저장

    private struct Record: Codable {
        var plan: String
        var startedAt: Int64?
        var trialEndsAt: Int64?
        var expiresAt: Int64?
        var autoRenew: Bool
        var organization: String?
        var verifiedAt: Int64?
    }

    private func save(_ value: Entitlement) {
        entitlement = value
        let record = Record(plan: value.plan.name, startedAt: value.startedAtMillis?.int64Value, trialEndsAt: value.trialEndsAtMillis?.int64Value,
                            expiresAt: value.expiresAtMillis?.int64Value, autoRenew: value.autoRenew, organization: value.organization,
                            verifiedAt: value.lastVerifiedAtMillis?.int64Value)
        if let data = try? JSONEncoder().encode(record) { defaults.set(data, forKey: Key.entitlement) }
    }

    private static func load() -> Entitlement {
        guard let data = UserDefaults.standard.data(forKey: Key.entitlement), let r = try? JSONDecoder().decode(Record.self, from: data) else {
            return Entitlement.companion.Free
        }
        let plan = PlanType.entries.first { $0.name == r.plan } ?? .free
        return Entitlement(plan: plan, startedAtMillis: r.startedAt.map { KotlinLong(value: $0) }, trialEndsAtMillis: r.trialEndsAt.map { KotlinLong(value: $0) },
                           expiresAtMillis: r.expiresAt.map { KotlinLong(value: $0) }, autoRenew: r.autoRenew, organization: r.organization,
                           lastVerifiedAtMillis: r.verifiedAt.map { KotlinLong(value: $0) })
    }
}

/// 결제창: 앱 안의 사파리 시트로 토스페이먼츠 테스트 결제창을 연다. 닫으면 주문 결과를 한 번 더 확인한다.
struct CheckoutSafari: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let controller = SFSafariViewController(url: url)
        controller.dismissButtonStyle = .close
        return controller
    }
    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

/// "24,000원"
func wonText(_ won: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    return "\(formatter.string(from: NSNumber(value: won)) ?? "\(won)")원"
}

func amountText(_ won: Int32) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    return formatter.string(from: NSNumber(value: won)) ?? "\(won)"
}
