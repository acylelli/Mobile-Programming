import SwiftUI
import Shared

/// 알람 만들기 5단계(안드로이드와 같은 흐름): 출발지 → 도착지 → 도착 시각·반복 → 준비 시간 → 계산 미리보기
struct CreateFlowView: View {
    @EnvironmentObject private var store: AlarmStore
    @Environment(\.dismiss) private var dismiss

    let original: Alarm
    @State private var draft: Alarm
    @State private var step = 1
    private let total = 5

    init(original: Alarm) {
        self.original = original
        _draft = State(initialValue: original)
    }

    private var isEditing: Bool { store.alarms.contains { $0.id == original.id } }

    var body: some View {
        VStack(spacing: 0) {
            StepHeader(step: step, total: total) {
                if step == 1 { dismiss() } else { withAnimation { step -= 1 } }
            }
            Group {
                switch step {
                case 1: PlaceStep(title: "어디에서 출발하나요?", selected: placeBinding(\.origin))
                case 2: PlaceStep(title: "어디로 가나요?", selected: placeBinding(\.destination))
                case 3: TimeStep(draft: $draft)
                case 4: PreparationStep(draft: $draft)
                default: PreviewStep(draft: $draft, isEditing: isEditing) { dismiss() }
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)

            if step < total {
                PrimaryButton(title: "다음", enabled: canGoNext) { withAnimation { step += 1 } }
                    .padding(.horizontal, 20).padding(.bottom, 12)
            }
        }
        .background(AppColor.background)
    }

    private var canGoNext: Bool {
        switch step {
        case 1: return !draft.origin.name.isEmpty
        case 2: return !draft.destination.name.isEmpty
        default: return true
        }
    }

    private func placeBinding(_ path: WritableKeyPath<Alarm, Place>) -> Binding<Place?> {
        Binding(
            get: { draft[keyPath: path].name.isEmpty ? nil : draft[keyPath: path] },
            set: { place in
                if let place {
                    draft[keyPath: path] = place
                    draft.plan = nil
                }
            }
        )
    }
}

/* ───────────── 1·2단계: 장소 검색 ───────────── */

private struct PlaceStep: View {
    let title: String
    @Binding var selected: Place?

    @State private var query = ""
    @State private var results: [Place] = []
    @State private var error: String?
    @State private var loading = false
    private let search = PlaceSearch(client: ProxyClient(baseUrl: ProxyClient.companion.DEFAULT_BASE_URL, client: nil))

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(title).font(AppFont.headline).foregroundStyle(AppColor.textPrimary).padding(.top, 24)
            SearchField(prompt: "역, 학교, 건물 이름", text: $query, autoFocus: selected == nil)
            if let selected, results.isEmpty, query.trimmingCharacters(in: .whitespaces).isEmpty {
                SectionLabel(text: "선택한 장소")
                PlaceRow(place: selected, selected: true)
            }
            if loading { ProgressView().frame(maxWidth: .infinity) }
            if let error { Text(error).font(AppFont.body).foregroundStyle(AppColor.textSecondary) }
            if !results.isEmpty {
                SectionLabel(text: "검색 결과")
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(results, id: \.self) { place in
                            Button { selected = place } label: { PlaceRow(place: place, selected: place == selected) }
                                .buttonStyle(.plain)
                        }
                    }
                }
                .scrollDismissesKeyboard(.immediately)
            }
        }
        .padding(.horizontal, 20)
        .task(id: query) {
            // 입력이 멈추고 0.4초 뒤에 검색(서버 호출을 줄인다)
            try? await Task.sleep(for: .milliseconds(400))
            if !Task.isCancelled { await run() }
        }
    }

    private func run() async {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard text.count >= 2 else { results = []; error = nil; return }
        loading = true
        defer { loading = false }
        do {
            let result = try await search.search(query: text)
            results = result.places.map(Place.init)
            error = result.error ?? (results.isEmpty ? "검색 결과가 없어요." : nil)
        } catch {
            self.error = "검색하지 못했어요."
        }
    }
}

private struct PlaceRow: View {
    let place: Place
    let selected: Bool

    var body: some View {
        HStack(spacing: 18) {
            Image(systemName: selected ? "mappin.circle.fill" : "mappin.and.ellipse")
                .font(.app(22))
                .foregroundStyle(selected ? AppColor.accent : AppColor.textTertiary)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 3) {
                Text(place.name).font(.app(18, .bold)).foregroundStyle(AppColor.textPrimary)
                Text(place.address).font(AppFont.body).foregroundStyle(AppColor.textSecondary).lineLimit(1)
            }
            Spacer()
            if selected { Image(systemName: "checkmark").foregroundStyle(AppColor.accent).font(.system(size: 17, weight: .bold)) }
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

/* ───────────── 3단계: 도착 시각·반복 ───────────── */

private struct TimeStep: View {
    @Binding var draft: Alarm
    private let labels = ["월", "화", "수", "목", "금", "토", "일"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text("몇 시까지 도착할까요?").font(AppFont.headline).foregroundStyle(AppColor.textPrimary).padding(.top, 24)
                DatePicker("도착 시각", selection: arrival, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel).labelsHidden()
                    .frame(maxWidth: .infinity)
                    .environment(\.locale, Locale(identifier: "ko_KR"))

                SectionLabel(text: "반복")
                HStack(spacing: 10) {
                    ChoiceChip(text: "한 번", selected: draft.repeatMask == 0) { draft.repeatMask = 0 }
                    ChoiceChip(text: "평일", selected: draft.repeatMask == 0b0011111) { draft.repeatMask = 0b0011111 }
                    ChoiceChip(text: "매일", selected: draft.repeatMask == 0b1111111) { draft.repeatMask = 0b1111111 }
                }
                HStack(spacing: 8) {
                    ForEach(0..<7, id: \.self) { index in
                        ChoiceChip(text: labels[index], selected: draft.repeatMask & (1 << index) != 0, square: true) {
                            draft.repeatMask ^= (1 << index)
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityLabel("\(labels[index])요일")
                    }
                }

                SectionLabel(text: "일정 이름 (선택)")
                TextField("예: 월요일 1교시", text: $draft.title)
                    .font(.app(17))
                    .padding(.horizontal, 20).frame(height: 56)
                    .background(RoundedRectangle(cornerRadius: 28).fill(AppColor.surface))
            }
            .padding(.horizontal, 20)
        }
    }

    private var arrival: Binding<Date> {
        Binding(
            get: { Calendar.current.date(from: DateComponents(hour: draft.hour, minute: draft.minute)) ?? Date() },
            set: { date in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
                draft.hour = parts.hour ?? 9
                draft.minute = parts.minute ?? 0
                draft.plan = nil
            }
        )
    }
}

/* ───────────── 4단계: 준비 시간 ───────────── */

private struct PreparationStep: View {
    @Binding var draft: Alarm

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("준비하는 데\n얼마나 걸리나요?").font(AppFont.headline).foregroundStyle(AppColor.textPrimary).padding(.top, 24)
            Text("일어나서 집을 나서기까지 걸리는 시간이에요.").font(AppFont.body).foregroundStyle(AppColor.textSecondary)

            HStack(spacing: 0) {
                Picker("시간", selection: hours) { ForEach(0..<3) { Text("\($0) 시간").tag($0) } }
                    .pickerStyle(.wheel)
                Picker("분", selection: minutes) { ForEach(Array(stride(from: 0, to: 60, by: 5)), id: \.self) { Text("\($0) 분").tag($0) } }
                    .pickerStyle(.wheel)
            }
            .frame(height: 180)
            .padding(.top, 10)

            HStack(spacing: 10) {
                ForEach([15, 30, 45, 60], id: \.self) { value in
                    ChoiceChip(text: KoreanText.shared.minutes(minutes: Int32(value)), selected: draft.preparationMinutes == value) {
                        draft.preparationMinutes = value
                    }
                }
            }
            .frame(maxWidth: .infinity)

            SectionLabel(text: "늦지 않게 일찍 도착할 여유").padding(.top, 28)
            HStack(spacing: 10) {
                ForEach([0, 5, 10, 15], id: \.self) { value in
                    ChoiceChip(text: value == 0 ? "없음" : "\(value)분", selected: draft.bufferMinutes == value) {
                        draft.bufferMinutes = value
                    }
                }
            }
        }
        .padding(.horizontal, 20)
        .onChange(of: draft.preparationMinutes) { _, _ in draft.plan = nil }
        .onChange(of: draft.bufferMinutes) { _, _ in draft.plan = nil }
    }

    private var hours: Binding<Int> {
        Binding(get: { draft.preparationMinutes / 60 }, set: { draft.preparationMinutes = $0 * 60 + draft.preparationMinutes % 60 })
    }

    private var minutes: Binding<Int> {
        Binding(get: { draft.preparationMinutes % 60 / 5 * 5 }, set: { draft.preparationMinutes = draft.preparationMinutes / 60 * 60 + $0 })
    }
}

/* ───────────── 5단계: 계산 미리보기 ───────────── */

private struct PreviewStep: View {
    @EnvironmentObject private var store: AlarmStore
    @Binding var draft: Alarm
    let isEditing: Bool
    let onDone: () -> Void

    @State private var loading = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text("계산 미리보기").font(AppFont.title).foregroundStyle(AppColor.textPrimary).padding(.top, 20)
                    if loading {
                        VStack(spacing: 12) {
                            ProgressView()
                            Text("교통 정보로 계산하는 중").font(AppFont.body).foregroundStyle(AppColor.textSecondary)
                        }
                        .frame(maxWidth: .infinity).padding(.top, 60)
                    } else if let plan = draft.plan {
                        result(plan)
                    } else if let error {
                        Text(error).font(AppFont.body).foregroundStyle(AppColor.danger)
                        Button("다시 계산") { Task { await calculate() } }.foregroundStyle(AppColor.accent)
                    }
                }
                .padding(.horizontal, 20)
            }
            PrimaryButton(title: isEditing ? "이대로 저장" : "이 시간으로 알람 설정", enabled: draft.plan != nil && !loading) {
                if draft.title.trimmingCharacters(in: .whitespaces).isEmpty { draft.title = draft.destination.name }
                draft.enabled = true
                let alarm = draft
                Task { await store.save(alarm) }
                onDone()
            }
            .padding(.horizontal, 20).padding(.bottom, 12)
        }
        .task { if draft.plan == nil { await calculate() } }
    }

    private func calculate() async {
        loading = true
        defer { loading = false }
        let result = await store.preview(draft)
        draft.plan = result.plan
        error = result.error
    }

    @ViewBuilder
    private func result(_ plan: SavedPlan) -> some View {
        HStack(alignment: .lastTextBaseline, spacing: 8) {
            Text(TimeText.hm(plan.wakeUp)).font(.app(72, .heavy)).monospacedDigit().foregroundStyle(AppColor.textPrimary)
            Text("에 깨워드릴게요").font(.app(18, .bold)).foregroundStyle(AppColor.textPrimary)
        }
        RouteStrip(line: plan.routeLine(origin: draft.origin.name, destination: draft.destination.name))
        HStack {
            Text("\(TimeText.hm(plan.wakeUp)) 기상")
            Spacer()
            Text("\(TimeText.hm(plan.departure)) 출발")
            Spacer()
            Text("\(TimeText.hm(plan.estimatedArrival)) 도착")
        }
        .font(AppFont.body).foregroundStyle(AppColor.textSecondary)

        VStack(spacing: 14) {
            breakdown("준비", draft.preparationMinutes)
            breakdown(travelLabel(plan), plan.travelMinutes)
            breakdown("여유", plan.bufferMinutes)
            Rectangle().fill(AppColor.divider).frame(height: 1)
            HStack {
                Text("\(draft.arrivalText) 도착까지 합계").font(.app(17, .bold))
                Spacer()
                Text("\(draft.preparationMinutes + plan.travelMinutes + plan.bufferMinutes)분")
                    .font(.app(20, .bold)).foregroundStyle(AppColor.accentText)
            }
        }
        .font(.app(17))
        .foregroundStyle(AppColor.textPrimary)
        .padding(24)
        .background(RoundedRectangle(cornerRadius: 24).fill(AppColor.surface))

        if plan.usedFallback {
            Text("지금은 교통 정보를 받지 못해 기본 이동시간 60분에 여유 5분을 더했어요. 알람 전에 다시 계산해요.")
                .font(AppFont.caption).foregroundStyle(AppColor.textSecondary)
        }
        if let weather = plan.weather, weather.extraMinutes > 0 || weather.umbrella { WeatherBanner(weather: weather) }
        if plan.isLate {
            Pill(text: "지금 출발해도 \(plan.lateByMinutes)분 늦어요", systemImage: "exclamationmark.triangle.fill",
                 foreground: AppColor.danger, background: AppColor.dangerContainer)
        }

        VStack(spacing: 0) {
            Toggle("비·눈 오면 일찍 깨우기", isOn: $draft.useWeather)
                .onChange(of: draft.useWeather) { _, _ in Task { await calculate() } }
            if draft.repeatMask != 0 {
                Rectangle().fill(AppColor.divider).frame(height: 1).padding(.vertical, 12)
                Toggle("공휴일에는 울리지 않기", isOn: $draft.skipHolidays)
                    .onChange(of: draft.skipHolidays) { _, _ in Task { await calculate() } }
            }
        }
        .font(.app(16))
        .tint(AppColor.accent)
        .padding(20)
        .background(RoundedRectangle(cornerRadius: 24).stroke(AppColor.divider))

        Text("\(TimeText.day(plan.targetArrival)) · \(draft.repeatText)").font(AppFont.caption).foregroundStyle(AppColor.textTertiary)
    }

    private func breakdown(_ label: String, _ minutes: Int) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text("\(minutes)분").fontWeight(.bold)
        }
    }

    /// "도보 + 버스 + 지하철 + 환승"
    private func travelLabel(_ plan: SavedPlan) -> String {
        var parts: [String] = []
        for mode in plan.segments.map(\.mode) {
            let name: String
            switch mode {
            case "WALK": name = "도보"
            case "BUS": name = "버스"
            case "SUBWAY": name = "지하철"
            case "TRAIN": name = "기차"
            default: name = "이동"
            }
            if !parts.contains(name) { parts.append(name) }
        }
        if plan.transferCount > 0 { parts.append("환승") }
        return parts.isEmpty ? "이동" : parts.joined(separator: " + ")
    }
}

/// 가로 노선 요약(기상 → 도착 구간을 시간 비율대로)
private struct RouteStrip: View {
    let line: RouteLine

    var body: some View {
        let legs = line.legs
        let total = max(1, legs.reduce(0) { $0 + Int($1.minutes) })
        GeometryReader { geo in
            // 끝의 도착 점(12)과 구간 사이 간격(4)을 빼고 남은 폭을 시간 비율로 나눈다.
            let available = max(0, geo.size.width - 12 - CGFloat(legs.count) * 4 - CGFloat(legs.count) * 10)
            HStack(spacing: 4) {
                ForEach(Array(legs.enumerated()), id: \.offset) { _, leg in
                    Capsule()
                        .fill(color(leg))
                        .frame(width: 10 + available * CGFloat(leg.minutes) / CGFloat(total), height: 12)
                }
                Circle().fill(AppColor.accent).frame(width: 12, height: 12)
            }
        }
        .frame(height: 12)
    }

    private func color(_ leg: RouteLeg) -> Color {
        switch leg.kind {
        case .preparation: return AppColor.dotted
        case .walk: return AppColor.walk
        default: return leg.colorArgb.map { Color(argb: $0.int64Value) } ?? AppColor.textSecondary
        }
    }
}
