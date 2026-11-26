import EventKit
import Foundation
import Shared
import UIKit
import Vision

/// 시간표 수업과 시간표로 만든 알람(안드로이드 DataStoreTimetableRepository 와 같은 역할)
@MainActor
final class TimetableStore: ObservableObject {
    static let shared = TimetableStore()

    struct Lesson: Codable, Identifiable, Hashable {
        var id = UUID().uuidString
        var name: String
        /// 월=0 … 일=6
        var day: Int
        var startMinutes: Int
        var room: String?

        var shared: TimetableClass {
            TimetableClass(id: id, name: name, day: Int32(day), startMinutes: Int32(startMinutes), endMinutes: nil, room: room)
        }
    }

    @Published private(set) var lessons: [Lesson] = []
    /// 예전에 시간표로 만든 알람(새로 만들면 바꾼다)
    @Published private(set) var alarmIds: [UUID] = []

    init() {
        if let data = UserDefaults.standard.data(forKey: "timetable.v1"), let value = try? JSONDecoder().decode([Lesson].self, from: data) { lessons = value }
        if let data = UserDefaults.standard.data(forKey: "timetableAlarms.v1"), let value = try? JSONDecoder().decode([UUID].self, from: data) { alarmIds = value }
    }

    var proposals: [TimetableAlarmProposal] { Timetables.shared.proposals(classes: lessons.map(\.shared)) }

    func upsert(_ lesson: Lesson) {
        if let index = lessons.firstIndex(where: { $0.id == lesson.id }) { lessons[index] = lesson } else { lessons.append(lesson) }
        persist()
    }

    func delete(_ id: String) {
        lessons.removeAll { $0.id == id }
        persist()
    }

    func clear() {
        lessons.removeAll()
        persist()
    }

    func add(_ new: [Lesson]) {
        lessons += new
        persist()
    }

    /// 사진의 글자를 Vision 으로 읽고, 해석은 안드로이드와 같은 shared 해석기에 맡긴다.
    func scan(_ image: UIImage) async -> String {
        guard let cgImage = image.cgImage else { return "사진을 열지 못했어요" }
        let lines = await Self.recognize(cgImage)
        let result = Timetables.shared.scan(lines: lines)
        if let error = result.error { return error }
        add(result.classes.map { Lesson(name: $0.name, day: Int($0.day), startMinutes: Int($0.startMinutes), room: $0.room) })
        return "수업 \(result.classes.count)개를 읽었어요. 틀린 곳은 눌러서 고쳐 주세요."
    }

    /// Vision 글자 인식 → 줄·단어 위치(이미지 픽셀, 위쪽 원점)
    nonisolated private static func recognize(_ image: CGImage) async -> [OcrLine] {
        await withCheckedContinuation { continuation in
            let request = VNRecognizeTextRequest { request, _ in
                let width = CGFloat(image.width), height = CGFloat(image.height)
                func rect(_ box: CGRect) -> (Int32, Int32, Int32, Int32) {
                    (Int32(box.minX * width), Int32((1 - box.maxY) * height), Int32(box.maxX * width), Int32((1 - box.minY) * height))
                }
                let lines: [OcrLine] = (request.results as? [VNRecognizedTextObservation] ?? []).compactMap { observation in
                    guard let candidate = observation.topCandidates(1).first else { return nil }
                    let text = candidate.string
                    var words: [OcrWord] = []
                    text.enumerateSubstrings(in: text.startIndex..., options: .byWords) { word, range, _, _ in
                        guard let word, let box = try? candidate.boundingBox(for: range)?.boundingBox else { return }
                        let r = rect(box)
                        words.append(OcrWord(text: word, left: r.0, top: r.1, right: r.2, bottom: r.3))
                    }
                    let r = rect(observation.boundingBox)
                    return OcrLine(text: text, left: r.0, top: r.1, right: r.2, bottom: r.3, words: words)
                }
                continuation.resume(returning: lines)
            }
            request.recognitionLanguages = ["ko-KR", "en-US"]
            request.recognitionLevel = .accurate
            do { try VNImageRequestHandler(cgImage: image).perform([request]) } catch { continuation.resume(returning: []) }
        }
    }

    /// 첫 수업마다 반복 알람을 만든다. 모두 만들어졌을 때만 예전 시간표 알람을 지운다(안드로이드와 같은 규칙).
    func createAlarms(origin: Place, destination: Place, store: AlarmStore) async -> String {
        var created: [UUID] = []
        var failed = 0
        for proposal in proposals {
            var alarm = Alarm.draft
            alarm.title = proposal.firstClassNames.first ?? destination.name
            alarm.origin = origin
            alarm.destination = destination
            alarm.hour = Int(proposal.arrivalMinutes) / 60
            alarm.minute = Int(proposal.arrivalMinutes) % 60
            alarm.repeatMask = Int(proposal.repeatMask)
            await store.save(alarm)
            if store.alarms.first(where: { $0.id == alarm.id })?.plan != nil { created.append(alarm.id) } else { failed += 1 }
        }
        let previous = alarmIds.filter { !created.contains($0) }
        if failed == 0 && !created.isEmpty {
            previous.forEach { store.delete($0) }
            alarmIds = created
            persist()
            return previous.isEmpty ? "알람 \(created.count)개를 만들었어요." : "알람 \(created.count)개를 만들고, 예전 시간표 알람 \(previous.count)개를 지웠어요."
        }
        alarmIds = Array(Set(alarmIds + created))
        persist()
        return "알람 \(created.count)개를 만들었어요. \(failed)개는 경로를 찾지 못해 만들지 못했어요."
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(lessons) { UserDefaults.standard.set(data, forKey: "timetable.v1") }
        if let data = try? JSONEncoder().encode(alarmIds) { UserDefaults.standard.set(data, forKey: "timetableAlarms.v1") }
    }
}

/// 기기 캘린더 앞으로 2주 일정(안드로이드 DeviceCalendarRepository 와 같은 범위·규칙)
struct CalendarItem: Identifiable, Hashable {
    let id: String
    let title: String
    let location: String
    let start: Date
    /// 매주 반복이면 요일(월=0 … 일=6)
    let weeklyDays: [Int]
}

enum CalendarReader {
    static let store = EKEventStore()

    static var authorized: Bool {
        let status = EKEventStore.authorizationStatus(for: .event)
        return status == .fullAccess || status == .authorized
    }

    static func requestAccess() async -> Bool {
        (try? await store.requestFullAccessToEvents()) ?? false
    }

    /// 하루 종일 일정은 도착 시각이 없어서 뺀다. 매주 반복 일정은 한 번만
    static func upcoming() -> [CalendarItem] {
        let now = Date()
        let predicate = store.predicateForEvents(withStart: now, end: now.addingTimeInterval(14 * 86_400), calendars: nil)
        var seen = Set<String>()
        return store.events(matching: predicate).filter { !$0.isAllDay }.compactMap { event in
            let weekly = weeklyDays(event)
            let key = weekly.isEmpty ? event.eventIdentifier ?? UUID().uuidString : (event.calendarItemExternalIdentifier ?? event.title ?? "")
            guard !seen.contains(key) else { return nil }
            seen.insert(key)
            return CalendarItem(id: key, title: (event.title ?? "").isEmpty ? "(제목 없음)" : event.title!, location: event.location ?? "",
                                start: event.startDate, weeklyDays: weekly)
        }
        .sorted { $0.start < $1.start }
    }

    private static func weeklyDays(_ event: EKEvent) -> [Int] {
        guard let rule = event.recurrenceRules?.first, rule.frequency == .weekly, rule.interval == 1 else { return [] }
        let isoDay: (Int) -> Int = { ($0 + 5) % 7 } // EKWeekday 일=1 … 토=7 → 월=0 … 일=6
        if let days = rule.daysOfTheWeek, !days.isEmpty { return days.map { isoDay($0.dayOfTheWeek.rawValue) }.sorted() }
        return [isoDay(Calendar.current.component(.weekday, from: event.startDate))]
    }
}
