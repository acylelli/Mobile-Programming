import PhotosUI
import SwiftUI
import Shared

/// 학교 시간표(안드로이드 UI-F-004): 사진·캘린더·수업 추가, 요일별 수업, 첫 수업 알람 만들기
struct TimetableView: View {
    @EnvironmentObject private var store: AlarmStore
    @StateObject private var timetable = TimetableStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var photo: PhotosPickerItem?
    @State private var editing: TimetableStore.Lesson?
    @State private var creatingAlarms = false
    @State private var confirmClear = false
    @State private var message: String?
    @State private var scanning = false
    private let days = ["월", "화", "수", "목", "금", "토", "일"]

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "학교 시간표") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("시간표를 넣으면 요일마다 첫 수업에 맞춰 알람을 만들어 드려요.").font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
                    Spacer().frame(height: 16.dp)
                    HStack(spacing: 8.dp) {
                        PhotosPicker(selection: $photo, matching: .images) { sourceTile("photo", "사진", loading: scanning) }.disabled(scanning)
                        NavigationLink { CalendarImportView() } label: { sourceTile("calendar", "캘린더") }
                        Button { editing = TimetableStore.Lesson(name: "", day: 0, startMinutes: 9 * 60) } label: { sourceTile("plus", "수업 추가") }
                    }
                    .buttonStyle(.plain)
                    Spacer().frame(height: Space.lg)
                    if timetable.lessons.isEmpty {
                        EmptyStateView(title: "아직 수업이 없어요", message: "시간표 캡처 사진이나 캘린더에서 가져오거나 직접 추가해 주세요.", icon: "calendar")
                    } else {
                        ForEach(0..<7, id: \.self) { day in
                            let lessons = timetable.lessons.filter { $0.day == day }.sorted { $0.startMinutes < $1.startMinutes }
                            if !lessons.isEmpty {
                                SectionLabel(text: "\(days[day])요일")
                                Spacer().frame(height: 8.dp)
                                VStack(spacing: 0) {
                                    ForEach(lessons) { lesson in
                                        Button { editing = lesson } label: { lessonRow(lesson) }.buttonStyle(.plain)
                                    }
                                }
                                .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
                                Spacer().frame(height: Space.md)
                            }
                        }
                        TextLink(text: "시간표 모두 지우기") { confirmClear = true }.frame(maxWidth: .infinity)
                    }
                    Spacer().frame(height: Space.lg)
                }
                .padding(.horizontal, Space.screen)
            }
            if !timetable.lessons.isEmpty {
                PrimaryButton(title: "알람 \(timetable.proposals.count)개 만들기") { creatingAlarms = true }
                    .padding(.horizontal, Space.screen).padding(.top, 8.dp).padding(.bottom, 16.dp)
            }
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .bottom) { if let message { Snackbar(text: message) { self.message = nil }.padding(.bottom, 80.dp) } }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task {
                scanning = true
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    message = await timetable.scan(image)
                } else {
                    message = "사진을 열지 못했어요"
                }
                scanning = false
                photo = nil
            }
        }
        .sheet(item: $editing) { lesson in LessonSheet(lesson: lesson, isNew: !timetable.lessons.contains { $0.id == lesson.id }) }
        .sheet(isPresented: $creatingAlarms) { TimetableAlarmSheet { message = $0 } }
        .alert("시간표를 모두 지울까요?", isPresented: $confirmClear) {
            Button("취소", role: .cancel) {}
            Button("지우기", role: .destructive) { timetable.clear() }
        } message: {
            Text("수업 목록만 지워지고, 이미 만든 알람은 그대로 남아요. 새 시간표로 알람을 다시 만들면 예전 알람은 새 알람으로 바뀌어요.")
        }
    }

    /// 안드로이드 ImportButton: 위아래 14dp, 아이콘 24dp, 모서리 12dp
    private func sourceTile(_ icon: String, _ label: String, loading: Bool = false) -> some View {
        VStack(spacing: 6.dp) {
            Group {
                if loading { ProgressView().tint(AppColor.accent) } else { Image(systemName: icon).font(.system(size: 20.dp)) }
            }
            .foregroundStyle(AppColor.textPrimary).frame(width: 24.dp, height: 24.dp)
            Text(label).font(AppFont.labelLarge).foregroundStyle(AppColor.textPrimary).lineLimit(1)
        }
        .padding(.vertical, 14.dp)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: Radius.tile).fill(AppColor.surface))
        .contentShape(Rectangle())
    }

    private func lessonRow(_ lesson: TimetableStore.Lesson) -> some View {
        HStack(spacing: 12.dp) {
            Text(String(format: "%02d:%02d", lesson.startMinutes / 60, lesson.startMinutes % 60))
                .font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary).frame(minWidth: 56.dp, alignment: .leading)
            VStack(alignment: .leading, spacing: 0) {
                Text(lesson.name).font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary).lineLimit(1)
                if let room = lesson.room, !room.isEmpty { Text(room).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary) }
            }
            Spacer()
        }
        .padding(.horizontal, 18.dp).padding(.vertical, 14.dp)
        .contentShape(Rectangle())
    }
}

/// 수업 추가·수정 시트(안드로이드 수업 수정 시트)
private struct LessonSheet: View {
    @StateObject private var timetable = TimetableStore.shared
    @Environment(\.dismiss) private var dismiss
    @State var lesson: TimetableStore.Lesson
    let isNew: Bool
    private let days = ["월", "화", "수", "목", "금", "토", "일"]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(isNew ? "수업 추가" : "수업 수정").font(AppFont.headlineSmall).foregroundStyle(AppColor.textPrimary)
            Spacer().frame(height: 16.dp)
            field("수업 이름", text: $lesson.name)
            Spacer().frame(height: 8.dp)
            field("강의실 (선택)", text: Binding(get: { lesson.room ?? "" }, set: { lesson.room = $0.isEmpty ? nil : $0 }))
            Spacer().frame(height: 16.dp)
            HStack(spacing: 6.dp) {
                ForEach(0..<7, id: \.self) { day in DayChip(text: days[day], selected: lesson.day == day) { lesson.day = day } }
            }
            Spacer().frame(height: 8.dp)
            SectionLabel(text: "시작 시각")
            WheelTimePicker(hour: Binding(get: { lesson.startMinutes / 60 }, set: { lesson.startMinutes = $0 * 60 + lesson.startMinutes % 60 }),
                            minute: Binding(get: { lesson.startMinutes % 60 }, set: { lesson.startMinutes = lesson.startMinutes / 60 * 60 + $0 }))
            Spacer().frame(height: 12.dp)
            PrimaryButton(title: "저장", enabled: !lesson.name.trimmingCharacters(in: .whitespaces).isEmpty) {
                timetable.upsert(lesson)
                dismiss()
            }
            if !isNew {
                Spacer().frame(height: 8.dp)
                SecondaryButton(title: "이 수업 삭제", danger: true) {
                    timetable.delete(lesson.id)
                    dismiss()
                }
            }
        }
        .padding(.horizontal, Space.screen).padding(.top, 28.dp).padding(.bottom, 12.dp)
        .presentationDetents([.height(isNew ? 580.dp : 640.dp)])
        .presentationDragIndicator(.visible)
        .presentationBackground(AppColor.background)
    }

    private func field(_ prompt: String, text: Binding<String>) -> some View {
        TextField("", text: text, prompt: Text(prompt).foregroundStyle(AppColor.textTertiary))
            .font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary)
            .padding(.horizontal, 16.dp).padding(.vertical, 15.dp)
            .background(RoundedRectangle(cornerRadius: Radius.field).fill(AppColor.surface))
    }
}

/// 첫 수업에 맞춰 알람 만들기 시트
private struct TimetableAlarmSheet: View {
    @EnvironmentObject private var store: AlarmStore
    @StateObject private var timetable = TimetableStore.shared
    @StateObject private var book = PlaceBook.shared
    @Environment(\.dismiss) private var dismiss
    @State private var origin: Place?
    @State private var destination: Place?
    @State private var picking: Bool?
    @State private var working = false
    let onDone: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("첫 수업에 맞춰 알람 만들기").font(AppFont.headlineSmall).foregroundStyle(AppColor.textPrimary)
            Spacer().frame(height: 6.dp)
            Text("요일마다 가장 이른 수업에 도착하도록 기상 알람을 맞춰요. 준비·여유 시간은 설정의 기본값을 써요.")
                .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
            Spacer().frame(height: 14.dp)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(timetable.proposals.enumerated()), id: \.offset) { _, proposal in
                    HStack(spacing: 0) {
                        Text(proposal.dayLabel).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary).frame(minWidth: 64.dp, alignment: .leading)
                        Text(String(format: "%02d:%02d 도착", proposal.arrivalMinutes / 60, proposal.arrivalMinutes % 60))
                            .font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary)
                        Spacer().frame(width: 8.dp)
                        Text(Array(NSOrderedSet(array: proposal.firstClassNames)).compactMap { $0 as? String }.joined(separator: ", "))
                            .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 18.dp).padding(.vertical, 12.dp)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
            Spacer().frame(height: 16.dp)
            SectionLabel(text: "출발지")
            Spacer().frame(height: 6.dp)
            PlaceRow(icon: "house", title: origin?.name ?? "눌러서 고르기", subtitle: origin?.address, selected: false) { picking = true }
            Spacer().frame(height: 10.dp)
            SectionLabel(text: "도착지 (학교)")
            Spacer().frame(height: 6.dp)
            PlaceRow(icon: "mappin.and.ellipse", title: destination?.name ?? "눌러서 고르기", subtitle: destination?.address, selected: false) { picking = false }
            if !timetable.alarmIds.isEmpty {
                Spacer().frame(height: 12.dp)
                Text("예전에 시간표로 만든 알람 \(timetable.alarmIds.count)개는 새 알람이 모두 만들어지면 지워져요.")
                    .font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
            }
            Spacer().frame(height: 16.dp)
            PrimaryButton(title: working ? "만드는 중…" : "알람 \(timetable.proposals.count)개 만들기", enabled: origin != nil && destination != nil && !working) {
                guard let origin, let destination else { return }
                working = true
                Task {
                    let result = await timetable.createAlarms(origin: origin, destination: destination, store: store)
                    working = false
                    onDone(result)
                    dismiss()
                }
            }
        }
        .padding(.horizontal, Space.screen).padding(.top, 28.dp).padding(.bottom, 12.dp)
        .presentationDetents([.height(CGFloat(timetable.proposals.count) * 52.dp + (timetable.alarmIds.isEmpty ? 425.dp : 475.dp))])
        .presentationDragIndicator(.visible)
        .presentationBackground(AppColor.background)
        .onAppear {
            origin = book.slot(.home)?.place
            destination = book.slot(.school)?.place
        }
        .sheet(item: Binding(get: { picking.map { PickTarget(isOrigin: $0) } }, set: { picking = $0?.isOrigin })) { target in
            PlacePickerSheet(title: target.isOrigin ? "어디에서 출발하나요?" : "학교를 골라 주세요") { place in
                if target.isOrigin { origin = place } else { destination = place }
            }
        }
    }

    private struct PickTarget: Identifiable { let isOrigin: Bool; var id: Bool { isOrigin } }
}

/// 장소 하나 고르기 시트(검색 결과·즐겨찾기·최근 검색)
struct PlacePickerSheet: View {
    let title: String
    var initialQuery = ""
    let onPick: (Place) -> Void
    @StateObject private var book = PlaceBook.shared
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [Place] = []
    private let search = PlaceSearch(client: ProxyClient(baseUrl: ProxyClient.companion.DEFAULT_BASE_URL, client: nil))

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(AppFont.headlineSmall).foregroundStyle(AppColor.textPrimary).padding(.top, 28.dp).padding(.bottom, 12.dp)
            SearchField(prompt: "장소, 주소 검색", text: $query)
            ScrollView {
                VStack(spacing: 0) {
                    let places = query.isEmpty ? book.saved.map(\.place) : results
                    ForEach(Array(places.enumerated()), id: \.offset) { _, place in
                        PlaceRow(icon: "mappin", title: place.name, subtitle: place.subtitle, selected: false, plainIcon: true) {
                            if !query.isEmpty { book.remember(place) }
                            onPick(place)
                            dismiss()
                        }
                    }
                }
                .padding(.top, 12.dp)
                TextLink(text: "취소") { dismiss() }.frame(maxWidth: .infinity).padding(.top, 8.dp)
            }
        }
        .padding(.horizontal, Space.screen)
        .presentationDetents([.large])
        .presentationBackground(AppColor.background)
        .onAppear { if query.isEmpty { query = initialQuery } }
        .task(id: query) {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled, !query.trimmingCharacters(in: .whitespaces).isEmpty else { return }
            results = ((try? await search.search(query: query))?.places ?? []).map(Place.init)
        }
    }
}

/// 캘린더 연동(안드로이드 UI-F-005): 매주 반복 일정은 시간표로, 다가오는 일정은 그 시각에 맞춘 알람으로
struct CalendarImportView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var timetable = TimetableStore.shared
    @State private var authorized = CalendarReader.authorized
    @State private var items: [CalendarItem] = []
    @State private var message: String?
    @State private var creating: EventDraft?
    private let days = ["월", "화", "수", "목", "금", "토", "일"]

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "캘린더 연동") { dismiss() }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if !authorized {
                        Spacer().frame(height: Space.lg)
                        EmptyStateView(title: "캘린더를 읽어도 될까요?",
                                       message: "일정의 시각과 장소로 알람을 만들 때만 읽어요. 캘린더를 바꾸거나 서버로 보내지 않아요.",
                                       icon: "calendar", actionTitle: "캘린더 권한 허용") {
                            Task {
                                authorized = await CalendarReader.requestAccess()
                                if authorized { items = CalendarReader.upcoming() } else { message = "캘린더를 읽으려면 캘린더 권한이 필요해요." }
                            }
                        }
                    } else {
                        loaded
                    }
                    Spacer().frame(height: Space.lg)
                }
                .padding(.horizontal, Space.screen)
            }
        }
        .background(AppColor.background)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { if authorized { items = CalendarReader.upcoming() } }
        .overlay(alignment: .bottom) { if let message { Snackbar(text: message) { self.message = nil } } }
        .fullScreenCover(item: $creating) { CreateFlowView(original: $0.alarm, destinationQuery: $0.query) }
    }

    @ViewBuilder private var loaded: some View {
        let weekly = items.filter { !$0.weeklyDays.isEmpty }
        let upcoming = items.filter { $0.weeklyDays.isEmpty }
        Text("기기 캘린더의 앞으로 2주 일정이에요. Google 캘린더 일정도 여기에 보여요.")
            .font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
        Spacer().frame(height: Space.md)
        if items.isEmpty {
            EmptyStateView(title: "앞으로 2주 일정이 없어요", message: "하루 종일 일정은 도착 시각이 없어서 보이지 않아요.", icon: "calendar")
        }
        if !weekly.isEmpty {
            SectionLabel(text: "매주 반복 일정")
            Spacer().frame(height: 8.dp)
            card {
                ForEach(weekly) { item in
                    row("매주 \(item.weeklyDays.map { days[$0] }.joined(separator: "·")) \(TimeText.hm(item.start))", item)
                }
            }
            Spacer().frame(height: 10.dp)
            SecondaryButton(title: "반복 일정 \(weekly.count)개를 시간표에 추가") {
                let lessons = weekly.flatMap { item in
                    item.weeklyDays.map { day in
                        TimetableStore.Lesson(name: item.title, day: day,
                                              startMinutes: Calendar.current.component(.hour, from: item.start) * 60 + Calendar.current.component(.minute, from: item.start),
                                              room: item.location.isEmpty ? nil : item.location)
                    }
                }
                timetable.add(lessons)
                message = "수업 \(lessons.count)개를 시간표에 추가했어요."
            }
            Spacer().frame(height: Space.lg)
        }
        if !upcoming.isEmpty {
            SectionLabel(text: "다가오는 일정")
            Spacer().frame(height: 8.dp)
            card {
                ForEach(upcoming) { item in
                    Button { creating = draft(for: item) } label: { row(TimeText.dateTimeShort(item.start), item) }
                        .buttonStyle(.plain)
                }
            }
            Spacer().frame(height: 8.dp)
            Text("일정을 누르면 그 시각에 맞춰 도착하는 알람을 만들 수 있어요.").font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
        }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0, content: content)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: Radius.group).fill(AppColor.surface))
    }

    /// 일정으로 만들 알람 초안과 도착지 검색어(장소)
    struct EventDraft: Identifiable {
        let alarm: Alarm
        let query: String
        var id: UUID { alarm.id }
    }

    private func draft(for item: CalendarItem) -> EventDraft {
        var alarm = Alarm.draft
        alarm.title = item.title
        alarm.hour = Calendar.current.component(.hour, from: item.start)
        alarm.minute = Calendar.current.component(.minute, from: item.start)
        alarm.repeatMask = 0
        alarm.oneTimeDate = item.start
        if let home = PlaceBook.shared.slot(.home)?.place { alarm.origin = home } else { alarm.useCurrentLocation = true; alarm.origin = Place(name: "현재 위치", address: "", latitude: 0, longitude: 0) }
        return EventDraft(alarm: alarm, query: item.location)
    }

    /// 안드로이드 EventRow: 좌우 18dp·위아래 13dp, 시각(labelLarge, 최소 96dp) + 제목·장소
    private func row(_ when: String, _ item: CalendarItem) -> some View {
        HStack(spacing: 10.dp) {
            Text(when).font(AppFont.labelLarge).foregroundStyle(AppColor.textSecondary).frame(minWidth: 96.dp, alignment: .leading)
            VStack(alignment: .leading, spacing: 0) {
                Text(item.title.isEmpty ? "(제목 없음)" : item.title).font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary).lineLimit(1)
                if !item.location.isEmpty { Text(item.location).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary).lineLimit(1) }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 18.dp).padding(.vertical, 13.dp)
        .contentShape(Rectangle())
    }
}
