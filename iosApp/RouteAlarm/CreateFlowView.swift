import SwiftUI
import Shared

/// 새 일정 만들기(안드로이드 UI-C-001~006): 출발지 → 도착지 → 도착 시각·반복 → 준비 시간 → 여유·알림 방식 → 계산 미리보기
struct CreateFlowView: View {
    @EnvironmentObject private var store: AlarmStore
    @Environment(\.dismiss) private var dismiss

    enum Step: Int { case origin, destination, time, preparation, buffer, result }

    let original: Alarm
    @State private var draft: Alarm
    @State private var step: Step = .origin
    private let inputSteps = 5

    /// 캘린더 일정에서 왔으면 도착지 검색어(장소)를 미리 채우고 2단계부터 시작한다.
    let destinationQuery: String

    init(original: Alarm, destinationQuery: String = "") {
        self.original = original
        self.destinationQuery = destinationQuery
        _draft = State(initialValue: original)
        _step = State(initialValue: destinationQuery.isEmpty ? .origin : .destination)
    }

    private var isEditing: Bool { store.alarms.contains { $0.id == original.id } }

    var body: some View {
        VStack(spacing: 0) {
            if step == .result {
                BackHeader(title: "계산 미리보기", onBack: back)
            } else {
                BackHeader(title: "", onBack: back) {
                    Text("\(step.rawValue + 1)/\(inputSteps)").font(AppFont.titleSmall).foregroundStyle(AppColor.textSecondary)
                }
                HStack(spacing: 6.dp) {
                    ForEach(0..<inputSteps, id: \.self) { index in
                        Capsule().fill(index <= step.rawValue ? AppColor.textPrimary : AppColor.outline).frame(height: 4.dp)
                    }
                }
                .padding(.horizontal, Space.screen)
            }

            Group {
                switch step {
                case .origin: PlaceStep(isOrigin: true, draft: $draft)
                case .destination: PlaceStep(isOrigin: false, draft: $draft, initialQuery: destinationQuery)
                case .time: TimeStep(draft: $draft)
                case .preparation: PreparationStep(draft: $draft)
                case .buffer: BufferStep(draft: $draft)
                case .result: PreviewStep(draft: $draft)
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)

            if step == .result {
                PrimaryButton(title: "이 시간으로 알람 설정", enabled: canSave) { save() }
                    .padding(.horizontal, Space.screen).padding(.top, 8.dp).padding(.bottom, 16.dp)
            } else {
                PrimaryButton(title: step == .buffer ? "계산 미리보기" : "다음", enabled: canGoNext) {
                    withAnimation { step = Step(rawValue: step.rawValue + 1) ?? .result }
                }
                .padding(.horizontal, Space.screen).padding(.top, 8.dp).padding(.bottom, 16.dp)
            }
        }
        .background(AppColor.background)
    }

    private func back() {
        if step == .origin { dismiss() } else { withAnimation { step = Step(rawValue: step.rawValue - 1) ?? .origin } }
    }

    private var canGoNext: Bool {
        switch step {
        case .origin: return draft.useCurrentLocation == true || !draft.origin.name.isEmpty
        case .destination: return !draft.destination.name.isEmpty
        default: return true
        }
    }

    /// 한 번만 울리는 일정인데 이미 늦었다면 저장할 수 없다.
    private var canSave: Bool {
        guard let plan = draft.plan else { return false }
        return !plan.isLate || draft.repeatMask != 0
    }

    private func save() {
        if draft.title.trimmingCharacters(in: .whitespaces).isEmpty { draft.title = draft.destination.name }
        draft.enabled = true
        let alarm = draft
        Task { await store.save(alarm) }
        dismiss()
    }
}

/// 단계 제목(안드로이드 StepTitle): 위 24dp, headlineLarge, 설명 bodyLarge, 아래 20dp
private struct StepTitle: View {
    let title: String
    var message: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: 24.dp)
            Text(title).font(AppFont.headlineLarge).foregroundStyle(AppColor.textPrimary).fixedSize(horizontal: false, vertical: true)
            if let message {
                Spacer().frame(height: 8.dp)
                Text(message).font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary)
            }
            Spacer().frame(height: 20.dp)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/* ───────────── 1·2단계: 장소 ───────────── */

private struct PlaceStep: View {
    let isOrigin: Bool
    @Binding var draft: Alarm
    var initialQuery = ""
    @StateObject private var book = PlaceBook.shared
    @State private var query = ""
    @State private var results: [Place]?
    @State private var error: String?
    @State private var loading = false
    @State private var registering: PlaceBook.Kind?
    @State private var deleteTarget: PlaceBook.Saved?
    private let search = PlaceSearch(client: ProxyClient(baseUrl: ProxyClient.companion.DEFAULT_BASE_URL, client: nil))

    private var selected: Place? {
        let place = isOrigin ? draft.origin : draft.destination
        return place.name.isEmpty ? nil : place
    }
    private var usingCurrentLocation: Bool { isOrigin && draft.useCurrentLocation == true }
    private var slotOrder: [PlaceBook.Kind] { isOrigin ? [.home, .school, .work] : [.school, .home, .work] }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                StepTitle(title: isOrigin ? "어디에서 출발하나요?" : "어디까지 가나요?")
                SearchField(prompt: registering.map { "\($0.label) 장소 검색" } ?? "장소, 주소 검색", text: $query, autoFocus: selected == nil && !usingCurrentLocation && initialQuery.isEmpty)
                if let registering {
                    Spacer().frame(height: 8.dp)
                    NoticeStrip(text: "고른 장소가 즐겨찾기 ‘\(registering.label)’에 저장돼요.", actionTitle: "취소") { self.registering = nil }
                }
                Spacer().frame(height: Space.lg)

                if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                    resultsView
                } else {
                    homeView
                }
                Spacer().frame(height: Space.lg)
            }
            .padding(.horizontal, Space.screen)
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear { if query.isEmpty && !initialQuery.isEmpty && selected == nil { query = initialQuery } }
        .task(id: query) {
            try? await Task.sleep(for: .milliseconds(400))
            if !Task.isCancelled { await run() }
        }
        .alert("즐겨찾기에서 삭제할까요?", isPresented: Binding(get: { deleteTarget != nil }, set: { if !$0 { deleteTarget = nil } })) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive) { if let target = deleteTarget { book.delete(target.id) } }
        } message: {
            Text("‘\(deleteTarget?.place.name ?? "")’ 장소를 즐겨찾기에서 지워요. 이미 만든 일정은 바뀌지 않아요.")
        }
    }

    @ViewBuilder private var resultsView: some View {
        if loading || results == nil {
            ProgressView().tint(AppColor.accent).frame(maxWidth: .infinity).padding(Space.xl)
        } else if let error {
            Text(error).font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary).padding(.vertical, Space.lg)
        } else if let results, results.isEmpty {
            Text("검색 결과가 없어요. 다른 이름이나 주소로 찾아보세요.").font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary)
                .padding(.vertical, Space.lg)
        } else if let results {
            SectionLabel(text: "검색 결과")
            Spacer().frame(height: 4.dp)
            ForEach(results, id: \.self) { place in
                PlaceRow(icon: "mappin", title: place.name, subtitle: place.subtitle,
                         selected: !usingCurrentLocation && selected?.isSame(place) == true, plainIcon: true) { select(place, fromSearch: true) }
            }
        }
    }

    @ViewBuilder private var homeView: some View {
        let slotPlaces = slotOrder.compactMap { book.slot($0)?.place }
        let listed = slotPlaces + book.customs.map(\.place) + book.recent
        if let selected, !usingCurrentLocation, !listed.contains(where: { $0.isSame(selected) }) {
            SectionLabel(text: "선택한 장소")
            Spacer().frame(height: 8.dp)
            PlaceRow(icon: "mappin.and.ellipse", title: selected.name, subtitle: selected.address.isEmpty ? nil : selected.address, selected: true) {}
            Spacer().frame(height: Space.lg)
        }
        if isOrigin {
            PlaceRow(icon: "location", title: "현재 위치", subtitle: "출발할 때 한 번만 조회해요", selected: usingCurrentLocation) {
                draft.useCurrentLocation = true
                draft.origin = Place(name: "현재 위치", address: "", latitude: 0, longitude: 0)
                draft.plan = nil
            }
            Spacer().frame(height: Space.lg)
        }
        SectionLabel(text: "즐겨찾기")
        Spacer().frame(height: 8.dp)
        VStack(spacing: 4.dp) {
            ForEach(slotOrder, id: \.self) { kind in
                if let slot = book.slot(kind) {
                    PlaceRow(icon: kind.icon, title: kind.label,
                             subtitle: slot.place.name == kind.label ? slot.place.address : "\(slot.place.name) | \(slot.place.address)",
                             selected: !usingCurrentLocation && selected?.isSame(slot.place) == true) { select(slot.place, fromSearch: false) }
                        .onLongPressGesture { deleteTarget = slot }
                } else {
                    PlaceRow(icon: kind.icon, title: kind.label, subtitle: "등록하기", selected: false) { registering = kind }
                }
            }
            ForEach(book.customs) { saved in
                PlaceRow(icon: "mappin.and.ellipse", title: saved.place.name, subtitle: saved.place.address,
                         selected: !usingCurrentLocation && selected?.isSame(saved.place) == true) { select(saved.place, fromSearch: false) }
                    .onLongPressGesture { deleteTarget = saved }
            }
        }
        if !book.recent.isEmpty {
            Spacer().frame(height: Space.lg)
            SectionLabel(text: "최근 검색")
            Spacer().frame(height: 4.dp)
            ForEach(book.recent, id: \.self) { place in
                PlaceRow(icon: "clock", title: place.name, subtitle: place.subtitle,
                         selected: !usingCurrentLocation && selected?.isSame(place) == true && !slotPlaces.contains { $0.isSame(place) },
                         plainIcon: true) { select(place, fromSearch: false) }
            }
        }
    }

    private func select(_ place: Place, fromSearch: Bool) {
        if let registering {
            book.register(registering, place)
            self.registering = nil
        } else if fromSearch {
            book.remember(place)
        }
        if isOrigin {
            draft.origin = place
            draft.useCurrentLocation = false
        } else {
            draft.destination = place
        }
        draft.plan = nil
        query = ""
    }

    private func run() async {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { results = nil; error = nil; return }
        loading = true
        defer { loading = false }
        do {
            let result = try await search.search(query: text)
            results = result.places.map(Place.init)
            error = result.error
        } catch {
            self.error = "장소를 검색하지 못했어요."
        }
    }
}

/// 장소 한 줄(안드로이드 PlaceRow): 선택되면 강조색 테두리·옅은 강조 배경·강조색 아이콘 타일
struct PlaceRow: View {
    let icon: String
    let title: String
    let subtitle: String?
    let selected: Bool
    var plainIcon = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14.dp) {
                if plainIcon {
                    Image(systemName: icon).font(.system(size: 19.dp)).foregroundStyle(selected ? AppColor.accentText : AppColor.textTertiary)
                        .frame(width: 22.dp)
                } else {
                    Image(systemName: icon).font(.system(size: 19.dp)).foregroundStyle(selected ? AppColor.onAccent : AppColor.textPrimary)
                        .frame(width: 42.dp, height: 42.dp)
                        .background(RoundedRectangle(cornerRadius: Radius.tile).fill(selected ? AppColor.accent : AppColor.surface))
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary).lineLimit(1)
                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(2)
                    }
                }
                Spacer(minLength: 0)
                if !plainIcon {
                    Image(systemName: "chevron.right").font(.system(size: 15.dp, weight: .semibold))
                        .foregroundStyle(selected ? AppColor.accentText : AppColor.textTertiary).frame(width: 24.dp)
                }
            }
            .padding(.horizontal, 16.dp).padding(.vertical, plainIcon ? 11.dp : 13.dp)
            .background(RoundedRectangle(cornerRadius: Radius.group).fill(selected ? AppColor.accentContainer : .clear))
            .overlay(RoundedRectangle(cornerRadius: Radius.group).strokeBorder(selected ? AppColor.accent : .clear, lineWidth: 1.5.dp))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/* ───────────── 3단계: 도착 시각·반복 ───────────── */

private struct TimeStep: View {
    @Binding var draft: Alarm
    @State private var pickingDate = false
    private let labels = ["월", "화", "수", "목", "금", "토", "일"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                StepTitle(title: "몇 시까지 도착할까요?")
                WheelTimePicker(
                    hour: Binding(get: { draft.hour }, set: { draft.hour = $0; draft.plan = nil }),
                    minute: Binding(get: { draft.minute }, set: { draft.minute = $0; draft.plan = nil })
                )

                Spacer().frame(height: Space.lg)
                SectionLabel(text: "반복")
                Spacer().frame(height: 10.dp)
                HStack(spacing: 8.dp) {
                    ChoicePill(text: "한 번", selected: draft.repeatMask == 0) { setMask(0) }
                    ChoicePill(text: "평일", selected: draft.repeatMask == 0b0011111) { setMask(0b0011111) }
                    ChoicePill(text: "매일", selected: draft.repeatMask == 0b1111111) { setMask(0b1111111) }
                }
                Spacer().frame(height: 10.dp)
                HStack(spacing: 6.dp) {
                    ForEach(0..<7, id: \.self) { index in
                        DayChip(text: labels[index], selected: draft.repeatMask & (1 << index) != 0) { setMask(draft.repeatMask ^ (1 << index)) }
                    }
                }
                if draft.repeatMask == 0 {
                    Spacer().frame(height: 12.dp)
                    dateRow
                }

                Spacer().frame(height: Space.lg)
                SectionLabel(text: "일정 이름 (선택)")
                Spacer().frame(height: 10.dp)
                TextField("", text: $draft.title, prompt: Text("예: 월요일 1교시").foregroundStyle(AppColor.textTertiary))
                    .font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary)
                    .padding(.horizontal, 16.dp).frame(height: 54.dp)
                    .background(RoundedRectangle(cornerRadius: Radius.field).fill(AppColor.surface))
            }
            .padding(.horizontal, Space.screen)
        }
        .sheet(isPresented: $pickingDate) {
            DatePicker("날짜", selection: Binding(get: { effectiveDate }, set: { draft.oneTimeDate = $0; draft.plan = nil }),
                       in: Calendar.current.startOfDay(for: Date())..., displayedComponents: .date)
                .datePickerStyle(.graphical).tint(AppColor.accent).padding(Space.screen)
                .environment(\.locale, Locale(identifier: "ko_KR"))
                .presentationDetents([.medium])
        }
    }

    private func setMask(_ mask: Int) {
        draft.repeatMask = mask
        draft.plan = nil
    }

    /// 따로 고르지 않으면 가장 가까운 날(오늘 목표 시각이 지났으면 내일)
    private var effectiveDate: Date {
        if let date = draft.oneTimeDate { return date }
        let now = Date()
        let today = Calendar.current.date(bySettingHour: draft.hour, minute: draft.minute, second: 0, of: now) ?? now
        return today > now ? today : today.addingTimeInterval(86_400)
    }

    private var dateRow: some View {
        let date = effectiveDate
        let calendar = Calendar.current
        let relative = calendar.isDateInToday(date) ? "오늘" : calendar.isDateInTomorrow(date) ? "내일" : nil
        let text = TimeText.day(date)
        return Button { pickingDate = true } label: {
            HStack(spacing: 12.dp) {
                Image(systemName: "calendar").font(.system(size: 18.dp)).foregroundStyle(AppColor.textPrimary).frame(width: 20.dp)
                Text("날짜").font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary)
                Spacer()
                Text(relative.map { "\($0) · \(text)" } ?? text).font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
                Image(systemName: "chevron.right").font(.system(size: 14.dp, weight: .semibold)).foregroundStyle(AppColor.textTertiary)
            }
            .padding(.horizontal, 16.dp).padding(.vertical, 15.dp)
            .background(RoundedRectangle(cornerRadius: Radius.field).fill(AppColor.surface))
        }
        .buttonStyle(.plain)
    }

}

/* ───────────── 4단계: 준비 시간 ───────────── */

private struct PreparationStep: View {
    @Binding var draft: Alarm

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                StepTitle(title: "준비하는 데\n얼마나 걸리나요?", message: "일어나서 집을 나서기까지 걸리는 시간이에요.")
                WheelDurationPicker(
                    minutes: Binding(get: { draft.preparationMinutes }, set: { draft.preparationMinutes = $0; draft.plan = nil }),
                    range: Prefs.preparationRange
                )
                Spacer().frame(height: Space.md)
                HStack(spacing: 8.dp) {
                    ForEach(Prefs.preparationPresets, id: \.self) { value in
                        ChoicePill(text: KoreanText.shared.minutes(minutes: Int32(value)), selected: draft.preparationMinutes == value) {
                            draft.preparationMinutes = value
                            draft.plan = nil
                        }
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, Space.screen)
        }
    }

}

/* ───────────── 5단계: 여유 시간·알림 방식 ───────────── */

private struct BufferStep: View {
    @Binding var draft: Alarm
    private let presets: [(String, Int)] = [("빠듯하게", 5), ("보통", 10), ("여유롭게", 20)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                StepTitle(title: "얼마나 여유 있게\n도착할까요?", message: "목표 시각보다 이만큼 일찍 도착하도록 계산해요.")
                VStack(spacing: 10.dp) {
                    ForEach(presets, id: \.1) { label, value in
                        option(label, value, selected: draft.bufferMinutes == value) { set(value) }
                    }
                    let custom = !presets.contains { $0.1 == draft.bufferMinutes }
                    option("직접 설정", draft.bufferMinutes, selected: custom) { if !custom { set(15) } } extra: {
                        if custom {
                            Slider(value: Binding(get: { Double(draft.bufferMinutes) }, set: { set(Int($0)) }), in: 0...60, step: 1)
                                .tint(AppColor.accent).padding(.horizontal, 12.dp)
                        }
                    }
                }
                Spacer().frame(height: Space.lg)
                SectionLabel(text: "알림 방식")
                Spacer().frame(height: 10.dp)
                HStack(spacing: 8.dp) {
                    ChoicePill(text: "기상 알람 + 출발 알림", selected: draft.wakeOnly != true) { draft.wakeOnly = false }
                    ChoicePill(text: "기상 알람만", selected: draft.wakeOnly == true) { draft.wakeOnly = true }
                }
                Spacer().frame(height: 8.dp)
                Text("출발 알림은 출발 10분 전과 출발 시각에 알려드려요.").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
            }
            .padding(.horizontal, Space.screen)
        }
    }

    private func set(_ value: Int) {
        draft.bufferMinutes = value
        draft.plan = nil
    }

    private func option(_ label: String, _ minutes: Int, selected: Bool, action: @escaping () -> Void,
                        @ViewBuilder extra: () -> some View = { EmptyView() }) -> some View {
        Button(action: action) {
            VStack(spacing: 0) {
                HStack(spacing: 14.dp) {
                    RadioDot(selected: selected)
                    Text(label).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                    Spacer()
                    Text(KoreanText.shared.minutes(minutes: Int32(minutes))).font(AppFont.titleMedium)
                        .foregroundStyle(selected ? AppColor.accentText : AppColor.textSecondary)
                }
                .padding(18.dp)
                extra().padding(.bottom, 12.dp)
            }
            .background(RoundedRectangle(cornerRadius: Radius.group).fill(selected ? AppColor.accentContainer : AppColor.background))
            .overlay(RoundedRectangle(cornerRadius: Radius.group).strokeBorder(selected ? AppColor.accent : AppColor.outline, lineWidth: selected ? 1.5.dp : 1.dp))
        }
        .buttonStyle(.plain)
    }
}

/* ───────────── 계산 미리보기 ───────────── */

private struct PreviewStep: View {
    @EnvironmentObject private var store: AlarmStore
    @Binding var draft: Alarm
    @State private var loading = false
    @State private var error: String?
    private let labels = ["월", "화", "수", "목", "금", "토", "일"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if loading {
                    VStack(spacing: Space.md) {
                        ProgressView().tint(AppColor.accent)
                        Text("교통 정보를 분석하고 있어요…").font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity).padding(.top, Space.xxl)
                } else if let plan = draft.plan {
                    result(plan)
                } else if let error {
                    VStack(spacing: Space.md) {
                        Image(systemName: "exclamationmark.circle").font(.system(size: 40.dp)).foregroundStyle(AppColor.danger)
                        Text(error).font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary).multilineTextAlignment(.center)
                        SecondaryButton(title: "다시 시도") { Task { await calculate() } }
                    }
                    .frame(maxWidth: .infinity).padding(.top, Space.xl)
                }
            }
            .padding(.horizontal, Space.screen)
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
        let line = plan.routeLine(origin: draft.origin.name, destination: draft.destination.name)
        Spacer().frame(height: 4.dp)
        HStack(alignment: .lastTextBaseline, spacing: 6.dp) {
            Text(TimeText.hm(plan.wakeUp)).font(AppFont.displayLarge).monospacedDigit().foregroundStyle(AppColor.textPrimary)
            Text("에 깨워드릴게요").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
        }
        VStack(spacing: 8.dp) {
            if plan.isLate {
                NoticeStrip(text: "지금 바로 출발해도 \(TimeText.hm(plan.estimatedArrival)) 도착 예정이에요. (\(plan.lateByMinutes)분 늦음)"
                            + (draft.repeatMask != 0 ? " 다음 회차부터는 정상적으로 알람이 울려요." : ""),
                            title: "현재 교통 상황에서는 \(TimeText.hm(plan.targetArrival))까지 도착하기 어려워요.",
                            icon: "exclamationmark.circle", danger: true)
            } else if plan.wakeUp <= Date() {
                NoticeStrip(text: "기상 시각은 이미 지났어요. 지금부터 준비하면 \(TimeText.hm(plan.departure))에 출발할 수 있어요.")
            }
            if plan.isOffline {
                NoticeStrip(text: "실시간 교통 정보를 가져오지 못해 마지막 경로 기준으로 계산했어요.", icon: "icloud.slash")
            }
        }
        .padding(.top, 4.dp)

        Spacer().frame(height: 18.dp)
        RouteBar(line: line, slackMinutes: plan.slackMinutes)
        Spacer().frame(height: 8.dp)
        HStack {
            Text("\(TimeText.hm(plan.wakeUp)) 기상")
            Spacer()
            Text("\(TimeText.hm(plan.departure)) 출발")
            Spacer()
            Text("\(TimeText.hm(plan.estimatedArrival)) 도착")
        }
        .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(1)

        Spacer().frame(height: 18.dp)
        let preparation = minutes(plan.wakeUp, plan.departure)
        let travel = minutes(plan.departure, plan.estimatedArrival)
        let slack = minutes(plan.estimatedArrival, plan.targetArrival)
        VStack(spacing: 10.dp) {
            breakdown("준비", preparation)
            breakdown(travelLabel(plan), travel)
            breakdown("여유", slack)
            Rectangle().fill(AppColor.outline).frame(height: 1.dp).padding(.vertical, 4.dp)
            HStack {
                Text("\(TimeText.hm(plan.targetArrival)) 도착까지 합계").font(AppFont.labelLarge).foregroundStyle(AppColor.textPrimary)
                Spacer()
                Text("\(preparation + travel + slack)분").font(.app(17, .medium)).monospacedDigit().foregroundStyle(AppColor.accentText)
            }
        }
        .padding(18.dp)
        .background(RoundedRectangle(cornerRadius: Radius.card).fill(AppColor.surface))

        Spacer().frame(height: 18.dp)
        HStack(spacing: 14.dp) {
            Text("반복").font(AppFont.labelLarge).foregroundStyle(AppColor.textPrimary)
            HStack(spacing: 6.dp) {
                ForEach(0..<7, id: \.self) { index in
                    DayChip(text: labels[index], selected: draft.repeatMask & (1 << index) != 0) {
                        draft.repeatMask ^= (1 << index)
                        Task { await calculate() }
                    }
                }
            }
        }
        if draft.repeatMask == 0 {
            Spacer().frame(height: 6.dp)
            Text("\(TimeText.day(plan.targetArrival)) 한 번만 울려요. 요일을 누르면 반복돼요.").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
        }

        if let delayed = DelayScenario.shared.wakeUpMillis(plan: plan.shared, preparationMinutes: Int32(draft.preparationMinutes),
                                                           bufferMinutes: Int32(draft.bufferMinutes), delayMinutes: 15),
           delayed.int64Value != plan.wakeUp.millis {
            Spacer().frame(height: 18.dp)
            DelayScenarioCard(plan: plan, adjusted: Date(millis: delayed.int64Value))
        }
        Spacer().frame(height: Space.lg)
    }

    private func minutes(_ from: Date, _ to: Date) -> Int { max(0, Int(to.timeIntervalSince(from) / 60)) }

    private func breakdown(_ label: String, _ minutes: Int) -> some View {
        HStack {
            Text(label).font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary).lineLimit(1)
            Spacer()
            Text("\(minutes)분").font(.app(17, .medium)).monospacedDigit().foregroundStyle(AppColor.textPrimary)
        }
    }

    /// "도보 + 버스 + 지하철 + 환승"(안드로이드 travelLabel)
    private func travelLabel(_ plan: SavedPlan) -> String {
        var parts: [String] = []
        if plan.segments.contains(where: { $0.mode == "WALK" }) { parts.append("도보") }
        for segment in plan.segments where segment.mode != "WALK" {
            let name: String
            switch segment.mode {
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

/// 경로 막대(안드로이드 RouteBar): 준비·도보·노선·여유를 시간 비율대로, 출발·환승·도착에 점
private struct RouteBar: View {
    let line: RouteLine
    let slackMinutes: Int

    var body: some View {
        let legs = line.legs
        let total = CGFloat(max(1, legs.reduce(0) { $0 + Int($1.minutes) } + max(0, slackMinutes)))
        let minimum = total * 0.04
        GeometryReader { geo in
            let dots = line.nodes.dropFirst().filter { [.departure, .arrival, .transfer].contains($0.kind) }.count
            let pieces = legs.filter { $0.minutes > 0 }.count + (slackMinutes > 0 ? 1 : 0)
            let spacing = 3.dp * CGFloat(pieces + dots - 1)
            let available = max(0, geo.size.width - spacing - CGFloat(dots) * 12.dp)
            let weights = legs.map { max(CGFloat($0.minutes), minimum) } + (slackMinutes > 0 ? [max(CGFloat(slackMinutes), minimum)] : [])
            let sum = max(1, weights.reduce(0, +))
            HStack(spacing: 3.dp) {
                ForEach(Array(legs.enumerated()), id: \.offset) { index, leg in
                    if leg.minutes > 0 {
                        Capsule().fill(color(leg)).frame(width: available * max(CGFloat(leg.minutes), minimum) / sum, height: 10.dp)
                    }
                    switch line.nodes[safe: index + 1]?.kind {
                    case .departure, .arrival: Circle().fill(AppColor.walk).frame(width: 12.dp, height: 12.dp)
                    case .transfer: Circle().fill(AppColor.textPrimary).frame(width: 12.dp, height: 12.dp)
                    default: EmptyView()
                    }
                }
                if slackMinutes > 0 {
                    Capsule().fill(AppColor.accent).frame(width: available * max(CGFloat(slackMinutes), minimum) / sum, height: 10.dp)
                }
            }
        }
        .frame(height: 12.dp)
    }

    private func color(_ leg: RouteLeg) -> Color {
        switch leg.kind {
        case .preparation: return AppColor.dotted
        case .walk: return AppColor.walk
        default: return leg.colorArgb.map { Color(argb: $0.int64Value) } ?? AppColor.textSecondary
        }
    }
}

/// "만약 15분 늦어진다면?"(안드로이드 DelayScenarioCard). 무료 플랜 문구
private struct DelayScenarioCard: View {
    let plan: SavedPlan
    let adjusted: Date

    var body: some View {
        let shift = Int(plan.wakeUp.timeIntervalSince(adjusted) / 60)
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("만약 15분 늦어진다면?").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                Spacer()
                TagBadge(text: "PRO", foreground: AppColor.background, background: AppColor.textPrimary)
            }
            Spacer().frame(height: 10.dp)
            HStack(spacing: 8.dp) {
                Text(TimeText.hm(plan.wakeUp)).font(.app(23, .medium)).strikethrough().foregroundStyle(AppColor.textTertiary)
                Text("→").font(AppFont.titleMedium).foregroundStyle(AppColor.textTertiary)
                Text(TimeText.hm(adjusted)).font(.app(23, .medium)).foregroundStyle(AppColor.accentText)
                Pill(text: "자동 조정", foreground: AppColor.accentText, background: AppColor.accentContainer)
            }
            if shift > 15 {
                Spacer().frame(height: 6.dp)
                Text("이동 15분 + 지연 여유 \(shift - 15)분만큼 앞당겨요.").font(AppFont.labelMedium).foregroundStyle(AppColor.textPrimary)
            }
            Spacer().frame(height: 6.dp)
            Text("무료 플랜은 출발 전 1회 확인 후 알림만 보내요. PRO는 출발 직전까지 다시 계산해 알람을 옮겨요.")
                .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
        }
        .padding(18.dp)
        .background(RoundedRectangle(cornerRadius: Radius.card).fill(AppColor.background))
        .overlay(RoundedRectangle(cornerRadius: Radius.card).strokeBorder(AppColor.textPrimary, lineWidth: 1.5.dp))
    }
}
