import Foundation
import Shared

/// 알람 목록 저장(UserDefaults)과 계산·예약. 계산·판단 규칙은 안드로이드와 같은 shared 모듈 코드다.

/// 알람 한 회차에 일어난 일(안드로이드 AlarmHistoryEvent 와 같은 종류·문구)
struct AlarmRecord: Codable, Identifiable, Hashable {
    enum Kind: String, Codable { case created, recalculated, adjusted, suggested, applied, fired, dismissed, snoozed, refreshFailed }
    var id = UUID()
    var alarmId: UUID
    var kind: Kind
    var at: Date
    /// 그때 이동시간(분)
    var travelMinutes: Int?
    /// 끄기·다시 울림: 예정 기상 시각. 조정: 새 기상 시각
    var plannedWakeUp: Date?
    /// 조정: 이전 기상 시각
    var previousWakeUp: Date?
    /// 실시간으로 확인한 이 회차의 출발 시각(요일별 이동시간 학습)
    var departure: Date?
    /// 실시간 교통 정보로 계산한 값인지
    var realtime: Bool?

    var title: String {
        switch kind {
        case .created: return "알람 생성"
        case .recalculated: return "교통 정보 재계산"
        case .adjusted: return "알람 자동 조정"
        case .suggested: return "알람 변경 추천"
        case .applied: return "추천 변경 적용"
        case .fired: return "알람 울림"
        case .dismissed: return "알람 종료"
        case .snoozed: return "다시 울림"
        case .refreshFailed: return "교통 정보 조회 실패"
        }
    }

    /// shared 규칙에 넘길 이력
    var event: AlarmEvent {
        let type: String
        switch kind {
        case .dismissed: type = "DISMISSED"
        case .snoozed: type = "SNOOZED"
        case .adjusted: type = "AUTO_ADJUSTED"
        case .applied: type = "ADJUSTMENT_APPLIED"
        case .suggested: type = "ADJUSTMENT_SUGGESTED"
        default: type = kind.rawValue.uppercased()
        }
        return AlarmEvent(type: type, occurredAtMillis: at.millis,
                          newWakeUpMillis: plannedWakeUp.map { KotlinLong(value: $0.millis) },
                          previousWakeUpMillis: previousWakeUp.map { KotlinLong(value: $0.millis) })
    }
}

/// 설정 화면의 측정 지표
struct StoreMetrics: Codable {
    var apiCalls = 0
    var apiFailures = 0
    var recalculations = 0
}

@MainActor
final class AlarmStore: ObservableObject {
    /// 알림을 눌렀을 때(AppDelegate) 같은 저장소를 쓰기 위해 하나만 둔다.
    static let shared = AlarmStore()

    @Published private(set) var alarms: [Alarm] = []
    @Published private(set) var refreshing: Set<UUID> = []
    @Published private(set) var records: [AlarmRecord] = []
    @Published private(set) var metrics = StoreMetrics()
    /// 지금 울리고 있는 알람(기상 알람 화면을 띄운다)
    @Published var ringing: UUID?
    /// 방금 지운 알람(되돌리기)
    @Published private(set) var lastDeleted: Alarm?

    private let planner = TripPlanner(client: ProxyClient(baseUrl: ProxyClient.companion.DEFAULT_BASE_URL, client: nil))
    private let key = "alarms.v2"

    init() {
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: key), let saved = try? JSONDecoder().decode([Alarm].self, from: data) {
            alarms = saved
        }
        if let data = defaults.data(forKey: "records.v1"), let saved = try? JSONDecoder().decode([AlarmRecord].self, from: data) {
            records = saved
        }
        if let data = defaults.data(forKey: "metrics.v1"), let saved = try? JSONDecoder().decode(StoreMetrics.self, from: data) {
            metrics = saved
        }
    }

    func records(for id: UUID) -> [AlarmRecord] {
        records.filter { $0.alarmId == id }.sorted { $0.at > $1.at }
    }

    // MARK: 울림·다시 울림·끄기

    func dismissRinging(_ id: UUID) {
        let planned = alarms.first { $0.id == id }?.plan?.wakeUp
        record(id, .dismissed, planned: planned)
        AlarmNotifier.cancelWakeRepeats(id)
        ringing = nil
        // 다음 회차로 넘어가 다시 계산·예약한다.
        Task { await refresh(id) }
    }

    func snooze(_ id: UUID, minutes: Int) async {
        record(id, .snoozed, planned: alarms.first { $0.id == id }?.plan?.wakeUp)
        ringing = nil
        if let alarm = alarms.first(where: { $0.id == id }) {
            await AlarmNotifier.scheduleSnooze(alarm, after: minutes)
        }
    }

    /// 지운 알람을 같은 id·같은 계획으로 되살린다(스낵바 [되돌리기]).
    func restoreDeleted() async {
        guard let alarm = lastDeleted else { return }
        lastDeleted = nil
        await save(alarm)
    }

    private func record(_ id: UUID, _ kind: AlarmRecord.Kind, planned: Date? = nil, previous: Date? = nil,
                        plan: SavedPlan? = nil) {
        let source = plan ?? alarms.first { $0.id == id }?.plan
        records.append(AlarmRecord(alarmId: id, kind: kind, at: Date(), travelMinutes: source?.travelMinutes, plannedWakeUp: planned,
                                   previousWakeUp: previous, departure: plan?.departure, realtime: plan.map { !$0.isOffline }))
        // 90일 넘은 기록은 정리한다.
        let limit = Date().addingTimeInterval(-90 * 86_400)
        records.removeAll { $0.at < limit }
        if let data = try? JSONEncoder().encode(records) { UserDefaults.standard.set(data, forKey: "records.v1") }
    }

    // MARK: 이력 분석(shared 규칙)

    var events: [AlarmEvent] { records.map(\.event) }

    func events(for id: UUID) -> [AlarmEvent] { records.filter { $0.alarmId == id }.map(\.event) }

    /// 최근 30일 기상·조정 통계(모든 일정)
    var stats: AlarmStats { AlarmHistoryRules.shared.stats(events: events, nowMillis: Date().millis) }

    /// 이 일정의 기상 습관 제안(제안을 적용·넘긴 뒤 기록만)
    func habit(for alarm: Alarm) -> WakeUpHabit? {
        AlarmHistoryRules.shared.habit(events: events(for: alarm.id), nowMillis: Date().millis,
                                       sinceMillis: alarm.habitHandledAt?.millis ?? 0)
    }

    func applyHabit(_ alarm: Alarm, extra: Int) async {
        var updated = alarm
        updated.preparationMinutes += extra
        updated.habitHandledAt = Date()
        updated.plan = nil
        await save(updated)
    }

    func dismissHabit(_ alarm: Alarm) {
        update(alarm.id) { $0.habitHandledAt = Date() }
    }

    /// 실시간으로 확인한 실제 이동시간(요일별 학습용)
    func travelRecords(for id: UUID) -> [TravelRecord] {
        records.filter { $0.alarmId == id && $0.kind == .recalculated && $0.realtime == true }
            .compactMap { record in
                guard let departure = record.departure, let travel = record.travelMinutes else { return nil }
                return TravelRecord(departureMillis: departure.millis, travelMinutes: Int32(travel))
            }
    }

    /// 요일별 이동시간 요약(상세 화면 카드)
    func travelPattern(for id: UUID) -> TravelPatternSummary? {
        TravelHistory.shared.summary(records: travelRecords(for: id), nowMillis: Date().millis)
    }

    /// 같은 요일·시간대 기록(3번 이상이면 이동시간 안정화에 섞는다)
    private func slotTravel(for id: UUID, departure: Date) -> [KotlinInt] {
        TravelHistory.shared.slotMinutes(records: travelRecords(for: id), departureMillis: departure.millis, nowMillis: Date().millis)
    }

    // MARK: 알람 변경 추천

    func applyPending(_ id: UUID) async {
        guard let alarm = alarms.first(where: { $0.id == id }), let pending = alarm.pendingPlan else { return }
        record(id, .applied, planned: pending.wakeUp, previous: alarm.plan?.wakeUp, plan: pending)
        update(id) { $0.plan = pending; $0.pendingPlan = nil }
        if let updated = alarms.first(where: { $0.id == id }) { await AlarmNotifier.schedule(updated, pending) }
    }

    func dismissPending(_ id: UUID) {
        update(id) { $0.pendingPlan = nil }
    }

    private func count(_ change: (inout StoreMetrics) -> Void) {
        change(&metrics)
        if let data = try? JSONEncoder().encode(metrics) { UserDefaults.standard.set(data, forKey: "metrics.v1") }
    }

    /// 만들기 5단계에서 이미 계산한 계획과 함께 저장한다. 계획이 없으면 바로 계산한다.
    func save(_ alarm: Alarm) async {
        if let index = alarms.firstIndex(where: { $0.id == alarm.id }) {
            alarms[index] = alarm
        } else {
            alarms.append(alarm)
        }
        persist()
        if !records.contains(where: { $0.alarmId == alarm.id }) { record(alarm.id, .created) }
        if let plan = alarm.plan, plan.wakeUp > Date() {
            await AlarmNotifier.schedule(alarm, plan)
        } else {
            await refresh(alarm.id)
        }
    }

    /// 저장 전에 계산만 해 본다(5단계 계산 미리보기).
    func preview(_ alarm: Alarm) async -> (plan: SavedPlan?, error: String?) {
        let now = Date()
        let lead = 60 + alarm.bufferMinutes + alarm.preparationMinutes
        guard let target = nextTarget(alarm, lead: lead, now: now) else { return (nil, "다음 알람 날짜를 찾지 못했어요.") }
        guard let origin = await resolveOrigin(alarm) else { return (nil, "현재 위치를 확인하지 못했어요. 위치 권한을 확인해 주세요.") }
        do {
            let result = try await planner.plan(
                origin: origin.shared, destination: alarm.destination.shared, targetArrivalMillis: target,
                preparationMinutes: Int32(alarm.preparationMinutes), bufferMinutes: Int32(alarm.bufferMinutes),
                useWeather: alarm.useWeather, nowMillis: now.millis,
                recentTravelMinutes: slotTravel(for: alarm.id, departure: Date(millis: target).addingTimeInterval(-Double(lead) * 60))
            )
            return (result.plan.map { SavedPlan($0, calculatedAt: now) }, result.error)
        } catch {
            return (nil, "계산하지 못했어요.")
        }
    }

    /// 홈에 보여 줄 가장 가까운 알람
    var nextAlarm: Alarm? {
        alarms.filter { $0.enabled && $0.plan != nil }.min { $0.plan!.wakeUp < $1.plan!.wakeUp }
    }

    /// 출발지를 현재 위치로 정한 일정은 계산할 때마다 위치를 한 번 조회한다.
    private func resolveOrigin(_ alarm: Alarm) async -> Place? {
        guard alarm.useCurrentLocation == true else { return alarm.origin }
        guard let coordinate = await LocationProvider.shared.current() else { return nil }
        return Place(name: "현재 위치", address: "", latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    private func nextTarget(_ alarm: Alarm, lead: Int, now: Date) -> Int64? {
        // 1회 일정에 날짜를 골랐으면 그 날의 목표 시각
        if alarm.repeatMask == 0, let date = alarm.oneTimeDate,
           let target = Calendar.current.date(bySettingHour: alarm.hour, minute: alarm.minute, second: 0, of: date) {
            return target.millis
        }
        return Schedules.shared.nextTargetMillis(
            hour: Int32(alarm.hour), minute: Int32(alarm.minute), repeatMask: Int32(alarm.repeatMask),
            nowMillis: now.millis, leadMinutes: Int32(lead), skipHolidays: alarm.skipHolidays
        )?.int64Value
    }

    func delete(_ id: UUID) {
        lastDeleted = alarms.first { $0.id == id }
        alarms.removeAll { $0.id == id }
        AlarmNotifier.cancel(id)
        persist()
    }

    func setEnabled(_ id: UUID, _ enabled: Bool) async {
        guard let index = alarms.firstIndex(where: { $0.id == id }) else { return }
        alarms[index].enabled = enabled
        persist()
        if enabled { await refresh(id) } else { AlarmNotifier.cancel(id) }
    }

    func refreshAll() async {
        for alarm in alarms where alarm.enabled { await refresh(alarm.id) }
    }

    /// 다음 회차를 찾아 교통 정보로 기상 시각을 계산하고 알림을 다시 예약한다.
    func refresh(_ id: UUID) async {
        guard let alarm = alarms.first(where: { $0.id == id }), alarm.enabled else { return }
        refreshing.insert(id)
        defer { refreshing.remove(id) }

        let now = Date()
        let lead = alarm.plan?.leadMinutes ?? (60 + alarm.bufferMinutes + alarm.preparationMinutes)
        guard let target = nextTarget(alarm, lead: lead, now: now) else {
            update(id) { $0.lastError = "다음 알람 날짜를 찾지 못했어요." }
            return
        }

        guard let origin = await resolveOrigin(alarm) else {
            update(id) { $0.lastError = "현재 위치를 확인하지 못했어요." }
            return
        }
        do {
            let result = try await planner.plan(
                origin: origin.shared, destination: alarm.destination.shared,
                targetArrivalMillis: target,
                preparationMinutes: Int32(alarm.preparationMinutes), bufferMinutes: Int32(alarm.bufferMinutes),
                useWeather: alarm.useWeather, nowMillis: now.millis,
                recentTravelMinutes: slotTravel(for: id, departure: alarm.plan?.departure ?? Date(millis: target).addingTimeInterval(-Double(lead) * 60))
            )
            count {
                $0.apiCalls += 1
                $0.recalculations += 1
                if result.plan?.usedFallback ?? true { $0.apiFailures += 1 }
            }
            guard let plan = result.plan else {
                record(id, .refreshFailed)
                update(id) { $0.lastError = result.error }
                return
            }
            let saved = SavedPlan(plan, calculatedAt: now)
            record(id, .recalculated, plan: saved)
            await apply(saved, to: id, now: now)
        } catch {
            update(id) { $0.lastError = "계산하지 못했어요." }
        }
    }

    /// 새 계산을 알람에 반영할지 shared AdjustmentEngine 으로 판단한다(안드로이드와 같은 규칙).
    /// 같은 회차가 아니면(다음 회차로 넘어감) 그대로 반영한다.
    private func apply(_ saved: SavedPlan, to id: UUID, now: Date) async {
        guard let alarm = alarms.first(where: { $0.id == id }) else { return }
        guard let current = alarm.plan, current.targetArrival == saved.targetArrival, current.wakeUp > now else {
            update(id) { $0.plan = saved; $0.pendingPlan = nil; $0.lastError = nil }
            if let updated = alarms.first(where: { $0.id == id }) { await AlarmNotifier.schedule(updated, saved) }
            return
        }
        let decision = AdjustmentEngine.shared.evaluate(
            currentWakeUp: current.wakeUp.kotlinInstant, proposedWakeUp: saved.wakeUp.kotlinInstant,
            autoAdjustEnabled: Prefs.bool(Prefs.autoAdjust), policy: AdjustmentPolicy.companion.Default, now: now.kotlinInstant
        )
        switch decision {
        case is AdjustmentDecisionKeep:
            // 흔들림은 무시하고 기상 시각은 그대로, 교통 상태·갱신 시각만 새로
            var kept = saved
            kept.wakeUp = current.wakeUp
            kept.departure = current.departure
            kept.estimatedArrival = current.estimatedArrival
            update(id) { $0.plan = kept; $0.lastError = nil }
        case is AdjustmentDecisionAutoAdjust:
            record(id, .adjusted, planned: saved.wakeUp, previous: current.wakeUp, plan: saved)
            update(id) { $0.plan = saved; $0.pendingPlan = nil; $0.lastError = nil }
            if let updated = alarms.first(where: { $0.id == id }) { await AlarmNotifier.schedule(updated, saved) }
        default:
            // 추천 또는 확인 필요: 알람은 그대로 두고 변경안을 카드로 보여 준다.
            record(id, .suggested, planned: saved.wakeUp, previous: current.wakeUp, plan: saved)
            update(id) {
                $0.pendingPlan = saved
                $0.pendingNeedsConfirmation = decision is AdjustmentDecisionRequireConfirmation
                $0.lastError = nil
            }
        }
    }

    private func update(_ id: UUID, _ change: (inout Alarm) -> Void) {
        guard let index = alarms.firstIndex(where: { $0.id == id }) else { return }
        change(&alarms[index])
        persist()
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(alarms) { UserDefaults.standard.set(data, forKey: key) }
    }
}
