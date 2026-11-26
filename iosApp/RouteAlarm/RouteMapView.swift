import MapKit
import SwiftUI
import Shared

/// 상세 화면 경로 지도의 선들(shared RouteMapLines 가 만든다)
struct RouteMapModel {
    struct Segment: Identifiable {
        let id: Int
        let coordinates: [CLLocationCoordinate2D]
        let isWalk: Bool
        let color: Color
    }

    let segments: [Segment]
    let origin: CLLocationCoordinate2D
    let destination: CLLocationCoordinate2D

    init?(alarm: Alarm, plan: SavedPlan) {
        let lines = RouteMapLines.shared.build(
            originLatitude: alarm.origin.latitude, originLongitude: alarm.origin.longitude,
            destinationLatitude: alarm.destination.latitude, destinationLongitude: alarm.destination.longitude,
            segments: plan.shared.segments
        )
        guard !lines.isEmpty else { return nil }
        segments = lines.enumerated().map { index, line in
            let values = line.points.map { $0.doubleValue }
            let coordinates = stride(from: 0, to: values.count - 1, by: 2).map {
                CLLocationCoordinate2D(latitude: values[$0], longitude: values[$0 + 1])
            }
            return Segment(id: index, coordinates: coordinates, isWalk: line.isWalk, color: Color(argb: line.colorArgb))
        }
        origin = CLLocationCoordinate2D(latitude: alarm.origin.latitude, longitude: alarm.origin.longitude)
        destination = CLLocationCoordinate2D(latitude: alarm.destination.latitude, longitude: alarm.destination.longitude)
    }
}

/// 경로 지도(안드로이드는 카카오맵, iOS 는 지도 앱과 같은 MapKit). 탑승은 노선색 굵은 선, 도보는 회색 가는 선
struct RouteMapView: View {
    let model: RouteMapModel

    var body: some View {
        Map(initialPosition: .rect(boundingRect)) {
            ForEach(model.segments) { segment in
                MapPolyline(coordinates: segment.coordinates)
                    .stroke(segment.isWalk ? AppColor.textTertiary : segment.color,
                            style: StrokeStyle(lineWidth: segment.isWalk ? 3 : 6, lineCap: .round, lineJoin: .round))
            }
            Annotation("출발", coordinate: model.origin) {
                Circle().fill(Color(hex: 0xE53935)).frame(width: 14.dp, height: 14.dp)
                    .overlay(Circle().strokeBorder(.white, lineWidth: 2))
            }
            Annotation("도착", coordinate: model.destination) {
                Circle().fill(Color(hex: 0x1E88E5)).frame(width: 14.dp, height: 14.dp)
                    .overlay(Circle().strokeBorder(.white, lineWidth: 2))
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .allowsHitTesting(false)
    }

    /// 모든 점이 들어오도록 여백을 둔 영역
    private var boundingRect: MKMapRect {
        let points = model.segments.flatMap(\.coordinates) + [model.origin, model.destination]
        var rect = MKMapRect.null
        for point in points {
            let p = MKMapPoint(point)
            rect = rect.union(MKMapRect(x: p.x, y: p.y, width: 0, height: 0))
        }
        return rect.insetBy(dx: -rect.width * 0.15 - 500, dy: -rect.height * 0.2 - 500)
    }
}
