import Foundation
import Shared

/// 알람 목록 저장(UserDefaults)과 계산·예약.
/// 계산은 안드로이드와 같은 Kotlin 코드(shared 모듈의 TripPlanner, Schedules)가 한다.
/// 알람 한 회차에 일어난 일(안드로이드 AlarmHistory 와 같은 종류)
struct AlarmRecord: Codable, Identifiable, Hashable {
    enum Kind: String, Codable { case created, recalculated, adjusted, dismissed, snoozed }
    var id = UUID()
    var alarmId: UUID
    var kind: Kind
    var at: Date
    /// 그때 이동시간(분)
    var travelMinutes: Int?
    /// 기상 알람 예정 시각(끈 기록에서 늦게 끈 시간을 구할 때)
    var plannedWakeUp: Date?

    var title: String {
        switch kind {
        case .created: return "알람 생성"
        case .recalculated: return "교통 정보 재계산"
        case .adjusted: return "알람 조정"
        case .dismissed: return "알람 종료"
        case .snoozed: return "다시 울림"
        }
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
        record(id, .snoozed)
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

    private func record(_ id: UUID, _ kind: AlarmRecord.Kind, planned: Date? = nil) {
        let travel = alarms.first { $0.id == id }?.plan?.travelMinutes
        records.append(AlarmRecord(alarmId: id, kind: kind, at: Date(), travelMinutes: travel, plannedWakeUp: planned))
        // 90일 넘은 기록은 정리한다.
        let limit = Date().addingTimeInterval(-90 * 86_400)
        records.removeAll { $0.at < limit }
        if let data = try? JSONEncoder().encode(records) { UserDefaults.standard.set(data, forKey: "records.v1") }
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
                useWeather: alarm.useWeather, nowMillis: now.millis, recentTravelMinutes: []
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
                useWeather: alarm.useWeather, nowMillis: now.millis, recentTravelMinutes: []
            )
            count {
                $0.apiCalls += 1
                $0.recalculations += 1
                if result.plan?.usedFallback ?? true { $0.apiFailures += 1 }
            }
            if let plan = result.plan {
                let saved = SavedPlan(plan, calculatedAt: now)
                update(id) { $0.plan = saved; $0.lastError = nil }
                record(id, .recalculated)
                if let updated = alarms.first(where: { $0.id == id }) { await AlarmNotifier.schedule(updated, saved) }
            } else {
                update(id) { $0.lastError = result.error }
            }
        } catch {
            update(id) { $0.lastError = "계산하지 못했어요." }
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
