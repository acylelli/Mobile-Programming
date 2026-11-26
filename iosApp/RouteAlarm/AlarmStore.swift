import Foundation
import Shared

/// 알람 목록 저장(UserDefaults)과 계산·예약.
/// 계산은 안드로이드와 같은 Kotlin 코드(shared 모듈의 TripPlanner, Schedules)가 한다.
@MainActor
final class AlarmStore: ObservableObject {
    @Published private(set) var alarms: [Alarm] = []
    @Published private(set) var refreshing: Set<UUID> = []

    private let planner = TripPlanner(client: ProxyClient(baseUrl: ProxyClient.companion.DEFAULT_BASE_URL, client: nil))
    private let key = "alarms.v2"

    init() {
        if let data = UserDefaults.standard.data(forKey: key),
           let saved = try? JSONDecoder().decode([Alarm].self, from: data) {
            alarms = saved
        }
    }

    /// 만들기 5단계에서 이미 계산한 계획과 함께 저장한다. 계획이 없으면 바로 계산한다.
    func save(_ alarm: Alarm) async {
        if let index = alarms.firstIndex(where: { $0.id == alarm.id }) {
            alarms[index] = alarm
        } else {
            alarms.append(alarm)
        }
        persist()
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
        do {
            let result = try await planner.plan(
                origin: alarm.origin.shared, destination: alarm.destination.shared, targetArrivalMillis: target,
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

    private func nextTarget(_ alarm: Alarm, lead: Int, now: Date) -> Int64? {
        Schedules.shared.nextTargetMillis(
            hour: Int32(alarm.hour), minute: Int32(alarm.minute), repeatMask: Int32(alarm.repeatMask),
            nowMillis: now.millis, leadMinutes: Int32(lead), skipHolidays: alarm.skipHolidays
        )?.int64Value
    }

    func delete(_ id: UUID) {
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

        do {
            let result = try await planner.plan(
                origin: alarm.origin.shared, destination: alarm.destination.shared,
                targetArrivalMillis: target,
                preparationMinutes: Int32(alarm.preparationMinutes), bufferMinutes: Int32(alarm.bufferMinutes),
                useWeather: alarm.useWeather, nowMillis: now.millis, recentTravelMinutes: []
            )
            if let plan = result.plan {
                let saved = SavedPlan(plan, calculatedAt: now)
                update(id) { $0.plan = saved; $0.lastError = nil }
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
