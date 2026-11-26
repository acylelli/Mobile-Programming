import MapKit
import SwiftUI
import Shared

/// 일정 상세(안드로이드 UI-D-002): 큰 기상 시각·스위치, 교통 상태, 경로, 지도, 다시 계산·지도 앱, 계산 내역, 일정 정보, 최근 기록, 삭제
struct AlarmDetailView: View {
    @EnvironmentObject private var store: AlarmStore
    @Environment(\.dismiss) private var dismiss
    let alarmId: UUID
    let onEdit: (Alarm) -> Void
    let onDeleted: () -> Void
    @State private var confirmDelete = false
    @State private var editingSound = false

    private var alarm: Alarm? { store.alarms.first { $0.id == alarmId } }

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: alarm?.title.isEmpty == false ? alarm!.title : "일정 상세", onBack: { dismiss() }) {
                if let alarm {
                    Button { onEdit(alarm) } label: {
                        Image(systemName: "pencil").font(.system(size: 20.dp)).frame(width: 48.dp, height: 48.dp)
                    }
                    .accessibilityLabel("수정")
                }
            }
            ScrollView {
                if let alarm { content(alarm) }
            }
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
        .alert("일정을 삭제할까요?", isPresented: $confirmDelete) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive) {
                store.delete(alarmId)
                onDeleted()
            }
        } message: {
            Text("알람도 함께 사라져요. 지운 직후에는 아래 되돌리기로 살릴 수 있어요.")
        }
    }

    @ViewBuilder
    private func content(_ alarm: Alarm) -> some View {
        let plan = alarm.plan
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: 8.dp)
            Text(alarm.repeatText).font(AppFont.labelMedium).foregroundStyle(AppColor.textSecondary)
            HStack {
                Text(plan.map { TimeText.hm($0.wakeUp) } ?? "--:--").font(AppFont.displayLarge).monospacedDigit()
                    .foregroundStyle(alarm.enabled ? AppColor.textPrimary : AppColor.textTertiary)
                Spacer()
                AppSwitch(isOn: Binding(get: { alarm.enabled }, set: { value in Task { await store.setEnabled(alarm.id, value) } }))
                    .accessibilityLabel("알람 켜기/끄기")
            }
            Text(plan.map { "\(alarm.destination.name) \(alarm.arrivalText) 도착 | \(TimeText.hm($0.departure)) 출발" }
                 ?? "\(alarm.destination.name) \(alarm.arrivalText) 도착")
                .font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary).lineLimit(1)
            if let plan {
                Spacer().frame(height: 12.dp)
                HStack(spacing: 10.dp) {
                    StatusPill(plan: plan)
                    Text(store.refreshing.contains(alarm.id) ? "확인 중…" : UpdatedText.ago(plan.calculatedAt))
                        .font(AppFont.bodyMedium).foregroundStyle(AppColor.textTertiary)
                }
            }

            VStack(spacing: 8.dp) {
                if !alarm.enabled { NoticeStrip(text: "알람이 꺼져 있어요. 켜면 교통 정보를 다시 확인해요.") }
                if let weather = plan?.weather { WeatherStrip(weather: weather) }
                LateRiskCard(alarm: alarm)
                if let error = alarm.lastError, plan != nil {
                    NoticeStrip(text: "\(error) 마지막 계산 결과로 알람을 유지해요.", icon: "icloud.slash")
                } else if let plan, plan.isOffline {
                    NoticeStrip(text: "실시간 교통 정보를 가져오지 못했어요. 마지막 계산 결과를 기준으로 알람을 유지합니다.", icon: "icloud.slash")
                }
                PendingAdjustmentCard(alarm: alarm)
                WakeUpHabitCard(alarm: alarm)
            }
            .padding(.top, 16.dp)

            if let plan {
                Spacer().frame(height: Space.lg)
                SectionLabel(text: "경로")
                Spacer().frame(height: 14.dp)
                RouteLineView(line: plan.routeLine(origin: alarm.origin.name, destination: alarm.destination.name))
                if let lines = RouteMapModel(alarm: alarm, plan: plan), !lines.segments.isEmpty {
                    Spacer().frame(height: Space.md)
                    RouteMapView(model: lines)
                        .frame(height: 220.dp)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.card))
                }
            }

            Spacer().frame(height: Space.lg)
            HStack(spacing: 10.dp) {
                SecondaryButton(title: store.refreshing.contains(alarm.id) ? "확인 중…" : "다시 계산") {
                    guard alarm.enabled, !store.refreshing.contains(alarm.id) else { return }
                    Task { await store.refresh(alarm.id) }
                }
                SecondaryButton(title: "지도 앱 열기") { openMaps(alarm) }
            }

            if let plan {
                Spacer().frame(height: Space.lg)
                SectionLabel(text: "계산 내역")
                Spacer().frame(height: 10.dp)
                let preparation = minutes(plan.wakeUp, plan.departure)
                let travel = minutes(plan.departure, plan.estimatedArrival)
                let slack = minutes(plan.estimatedArrival, plan.targetArrival)
                InfoCard {
                    InfoRow(label: "준비", value: "\(preparation)분")
                    InfoRow(label: "이동", value: "\(travel)분")
                    InfoRow(label: "여유", value: "\(slack)분")
                    Rectangle().fill(AppColor.outline).frame(height: 1.dp)
                    InfoRow(label: "\(TimeText.hm(plan.targetArrival)) 도착까지 합계", value: "\(preparation + travel + slack)분", emphasized: true)
                }
            }

            if let pattern = store.travelPattern(for: alarm.id) {
                Spacer().frame(height: Space.lg)
                SectionLabel(text: "요일별 이동시간")
                Spacer().frame(height: 10.dp)
                TravelPatternCard(pattern: pattern)
            }

            Spacer().frame(height: Space.lg)
            SectionLabel(text: "일정 정보")
            Spacer().frame(height: 10.dp)
            InfoCard {
                InfoRow(label: "출발지", value: alarm.origin.name)
                InfoRow(label: "도착지", value: alarm.destination.name)
                InfoRow(label: "반복", value: alarm.repeatLabel)
                InfoRow(label: "알림 방식", value: alarm.wakeOnly == true ? "기상 알람만" : "기상 알람 + 출발 알림")
                Rectangle().fill(AppColor.outline).frame(height: 1.dp)
                Button { editingSound = true } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 0) {
                            Text("알람음").font(AppFont.labelLarge).foregroundStyle(AppColor.textPrimary)
                            Text(alarm.sound.map(\.summary) ?? "설정 따르기 (\(SoundSetting.preferences.summary))").font(AppFont.bodySmall)
                                .foregroundStyle(alarm.sound == nil ? AppColor.textSecondary : AppColor.accentText).lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 14.dp, weight: .semibold)).foregroundStyle(AppColor.textTertiary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .sheet(isPresented: $editingSound) {
                    AlarmSoundSheet(title: "이 일정의 알람음", initial: alarm.effectiveSound, followSettings: alarm.sound == nil) { setting in
                        var updated = alarm
                        updated.sound = setting
                        Task { await store.save(updated) }
                    }
                }
                if alarm.repeatMask != 0 {
                    Rectangle().fill(AppColor.outline).frame(height: 1.dp)
                    HolidaySkipRow(alarm: alarm)
                }
            }

            Spacer().frame(height: Space.lg)
            SectionLabel(text: "최근 기록")
            Spacer().frame(height: 10.dp)
            let history = Array(store.records(for: alarm.id).prefix(10))
            if history.isEmpty {
                Text("아직 기록이 없어요").font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
            } else {
                InfoCard {
                    ForEach(Array(history.enumerated()), id: \.element.id) { index, record in
                        if index > 0 { Rectangle().fill(AppColor.outline).frame(height: 1.dp) }
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 2.dp) {
                                Text(record.title).font(AppFont.bodyMedium).foregroundStyle(AppColor.textPrimary)
                                if let travel = record.travelMinutes {
                                    Text("이동 \(KoreanText.shared.minutes(minutes: Int32(travel)))").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                                }
                            }
                            Spacer()
                            Text(TimeText.dateTime(record.at)).font(AppFont.bodySmall).foregroundStyle(AppColor.textTertiary)
                        }
                    }
                }
            }

            Spacer().frame(height: Space.lg)
            SecondaryButton(title: "일정 삭제", danger: true) { confirmDelete = true }
            Spacer().frame(height: Space.xl)
        }
        .padding(.horizontal, Space.screen)
    }

    private func minutes(_ from: Date, _ to: Date) -> Int { max(0, Int(to.timeIntervalSince(from) / 60)) }

    /// 도착지까지 대중교통 길찾기를 지도 앱으로 연다.
    private func openMaps(_ alarm: Alarm) {
        let origin = alarm.useCurrentLocation == true ? MKMapItem.forCurrentLocation()
            : MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: alarm.origin.latitude, longitude: alarm.origin.longitude)))
        if alarm.useCurrentLocation != true { origin.name = alarm.origin.name }
        let destination = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: alarm.destination.latitude, longitude: alarm.destination.longitude)))
        destination.name = alarm.destination.name
        MKMapItem.openMaps(with: [origin, destination], launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeTransit])
    }
}

/// 회색 정보 카드(안드로이드 InfoCard: 가로 18·세로 16dp, 줄 간격 12dp)
struct InfoCard<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 12.dp, content: content)
            .padding(.horizontal, 18.dp).padding(.vertical, 16.dp)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
    }
}

struct InfoRow: View {
    let label: String
    let value: String
    var emphasized = false
    var body: some View {
        HStack(spacing: 12.dp) {
            Text(label).font(emphasized ? AppFont.labelLarge : AppFont.bodyMedium)
                .foregroundStyle(emphasized ? AppColor.textPrimary : AppColor.textSecondary)
            Spacer()
            Text(value).font(AppFont.labelLarge).foregroundStyle(emphasized ? AppColor.accentText : AppColor.textPrimary)
                .lineLimit(1).multilineTextAlignment(.trailing)
        }
    }
}

/// "공휴일에는 울리지 않기" 스위치와 곧 건너뛸 공휴일 안내(안드로이드 HolidaySkipRow)
private struct HolidaySkipRow: View {
    @EnvironmentObject private var store: AlarmStore
    let alarm: Alarm

    var body: some View {
        let skipped = Schedules.shared.nextSkippedHoliday(hour: Int32(alarm.hour), minute: Int32(alarm.minute), repeatMask: Int32(alarm.repeatMask),
                                                         skipHolidays: alarm.skipHolidays, nowMillis: Date().millis)
        HStack(spacing: 12.dp) {
            VStack(alignment: .leading, spacing: 0) {
                Text("공휴일에는 울리지 않기").font(AppFont.labelLarge).foregroundStyle(AppColor.textPrimary)
                Text(skipped.map { "\($0)에는 울리지 않아요" }
                     ?? (alarm.skipHolidays ? "공휴일·대체공휴일에는 다음 반복 요일로 넘어가요" : "공휴일에도 평소처럼 울려요"))
                    .font(AppFont.bodySmall).foregroundStyle(skipped != nil ? AppColor.accentText : AppColor.textSecondary)
            }
            Spacer()
            AppSwitch(isOn: Binding(get: { alarm.skipHolidays }, set: { value in
                var updated = alarm
                updated.skipHolidays = value
                updated.plan = nil
                Task { await store.save(updated) }
            }))
        }
    }
}
