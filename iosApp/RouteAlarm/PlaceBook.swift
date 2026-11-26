import CoreLocation
import Foundation

/// 즐겨찾기(집·학교·회사·기타)와 최근 검색(안드로이드 SavedPlace·RecentSearch 와 같은 역할)
@MainActor
final class PlaceBook: ObservableObject {
    static let shared = PlaceBook()

    enum Kind: String, Codable, CaseIterable {
        case home, school, work, custom
        var label: String {
            switch self {
            case .home: return "집"
            case .school: return "학교"
            case .work: return "회사"
            case .custom: return "기타"
            }
        }
        var icon: String {
            switch self {
            case .home: return "house"
            case .school: return "graduationcap"
            case .work: return "briefcase"
            case .custom: return "place"
            }
        }
    }

    struct Saved: Codable, Identifiable, Hashable {
        var id = UUID()
        var kind: Kind
        var place: Place
    }

    @Published private(set) var saved: [Saved] = []
    @Published private(set) var recent: [Place] = []
    private static let recentLimit = 5

    init() {
        if let data = UserDefaults.standard.data(forKey: "savedPlaces.v1"), let value = try? JSONDecoder().decode([Saved].self, from: data) { saved = value }
        if let data = UserDefaults.standard.data(forKey: "recentPlaces.v1"), let value = try? JSONDecoder().decode([Place].self, from: data) { recent = value }
    }

    func slot(_ kind: Kind) -> Saved? { saved.first { $0.kind == kind } }
    var customs: [Saved] { saved.filter { $0.kind == .custom } }

    func register(_ kind: Kind, _ place: Place) {
        if kind != .custom { saved.removeAll { $0.kind == kind } }
        saved.append(Saved(kind: kind, place: place))
        persist()
    }

    func delete(_ id: UUID) {
        saved.removeAll { $0.id == id }
        persist()
    }

    /// 검색 결과에서 고른 장소를 최근 검색 맨 앞에(같은 곳은 하나만)
    func remember(_ place: Place) {
        recent.removeAll { $0.isSame(place) }
        recent.insert(place, at: 0)
        recent = Array(recent.prefix(Self.recentLimit))
        persist()
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(saved) { UserDefaults.standard.set(data, forKey: "savedPlaces.v1") }
        if let data = try? JSONEncoder().encode(recent) { UserDefaults.standard.set(data, forKey: "recentPlaces.v1") }
    }
}

/// 현재 위치를 한 번만 조회한다(안드로이드 FusedLocation 1회 조회와 같은 정책).
@MainActor
final class LocationProvider: NSObject, CLLocationManagerDelegate {
    static let shared = LocationProvider()
    private let manager = CLLocationManager()
    private var waiting: [CheckedContinuation<CLLocationCoordinate2D?, Never>] = []

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func current() async -> CLLocationCoordinate2D? {
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted { return nil }
        return await withCheckedContinuation { continuation in
            waiting.append(continuation)
            manager.requestLocation()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let coordinate = locations.last?.coordinate
        Task { @MainActor in self.finish(coordinate) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.finish(nil) }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            if status == .authorizedWhenInUse || status == .authorizedAlways {
                if !self.waiting.isEmpty { manager.requestLocation() }
            } else if status == .denied || status == .restricted {
                self.finish(nil)
            }
        }
    }

    private func finish(_ coordinate: CLLocationCoordinate2D?) {
        let all = waiting
        waiting.removeAll()
        all.forEach { $0.resume(returning: coordinate) }
    }
}
