import SwiftUI
import Shared

/// 일정 상세: 큰 기상 시각, 상태, 노선도(안드로이드 일정 상세와 같은 구성)
struct AlarmDetailView: View {
    @EnvironmentObject private var store: AlarmStore
    @Environment(\.dismiss) private var dismiss
    let alarmId: UUID
    let onEdit: (Alarm) -> Void
    @State private var confirmDelete = false

    private var alarm: Alarm? { store.alarms.first { $0.id == alarmId } }

    var body: some View {
        VStack(spacing: 0) {
            // 안드로이드 상세 화면처럼 둥근 유리 버튼 없이 화살표 · 제목 · 연필
            HStack(spacing: 16) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left").font(.system(size: 20, weight: .medium)).frame(width: 32, height: 44)
                }
                .accessibilityLabel("뒤로")
                Text(alarm?.title ?? "").font(AppFont.title).lineLimit(1)
                Spacer()
                if let alarm {
                    Button { onEdit(alarm) } label: {
                        Image(systemName: "pencil").font(.system(size: 20, weight: .medium)).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("편집")
                }
            }
            .foregroundStyle(AppColor.textPrimary)
            .padding(.horizontal, 16)
            .padding(.top, 4)

            ScrollView {
                if let alarm { content(alarm) }
            }
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
        .confirmationDialog("이 알람을 지울까요?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("삭제", role: .destructive) {
                store.delete(alarmId)
                dismiss()
            }
        }
    }

    private func content(_ alarm: Alarm) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(alarm.repeatText).font(AppFont.label).foregroundStyle(AppColor.textSecondary)
            HStack(alignment: .center) {
                Text(alarm.plan.map { TimeText.hm($0.wakeUp) } ?? "--:--")
                    .font(.app(72, .regular)).monospacedDigit()
                    .foregroundStyle(AppColor.textPrimary)
                Spacer()
                Toggle("", isOn: Binding(get: { alarm.enabled }, set: { value in Task { await store.setEnabled(alarm.id, value) } }))
                    .labelsHidden().tint(AppColor.accent)
            }
            if let plan = alarm.plan {
                Text("\(alarm.destination.name) \(alarm.arrivalText) 도착 | \(TimeText.hm(plan.departure)) 출발")
                    .font(.app(17)).foregroundStyle(AppColor.textSecondary)
                HStack(spacing: 12) {
                    StatusPill(plan: plan)
                    Text(UpdatedText.ago(plan.calculatedAt)).font(AppFont.body).foregroundStyle(AppColor.textTertiary)
                    Spacer()
                    if store.refreshing.contains(alarm.id) {
                        ProgressView()
                    } else {
                        Button { Task { await store.refresh(alarm.id) } } label: { Image(systemName: "arrow.clockwise") }
                            .foregroundStyle(AppColor.textSecondary).accessibilityLabel("다시 계산")
                    }
                }
                .padding(.top, 12)
                if let holiday = Schedules.shared.holidayName(millis: plan.targetArrival.millis) {
                    Pill(text: "\(holiday)에도 울려요", systemImage: "calendar", foreground: AppColor.accentText, background: AppColor.accentContainer)
                        .padding(.top, 10)
                }
                if plan.isLate {
                    Pill(text: "지금 출발해도 \(plan.lateByMinutes)분 늦어요", systemImage: "exclamationmark.triangle.fill",
                         foreground: AppColor.danger, background: AppColor.dangerContainer).padding(.top, 10)
                }
                if let weather = plan.weather, weather.extraMinutes > 0 || weather.umbrella {
                    WeatherBanner(weather: weather).padding(.top, 16)
                }

                SectionLabel(text: "경로").padding(.top, 36).padding(.bottom, 16)
                RouteLineView(line: plan.routeLine(origin: alarm.origin.name, destination: alarm.destination.name))
                if plan.usedFallback {
                    Text("교통 정보를 받지 못해 기본 이동시간 60분과 여유 +5분으로 계산했어요. 다시 계산하면 실제 경로로 바뀌어요.")
                        .font(AppFont.caption).foregroundStyle(AppColor.textTertiary).padding(.top, 12)
                }
            } else if let error = alarm.lastError {
                Text(error).font(AppFont.body).foregroundStyle(AppColor.danger).padding(.top, 8)
            }

            Button("알람 삭제") { confirmDelete = true }
                .font(AppFont.label).foregroundStyle(AppColor.danger)
                .frame(maxWidth: .infinity).padding(.top, 40)
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 32)
    }
}
