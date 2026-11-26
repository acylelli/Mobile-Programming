import SwiftUI

/// 분 고르기 시트(안드로이드 MinutePickerSheet): 큰 숫자, 추천값 알약, 슬라이더, 확인
struct MinutePickerSheet: View {
    let title: String
    let message: String
    let range: ClosedRange<Int>
    let presets: [Int]
    let onConfirm: (Int) -> Void
    @State private var value: Int
    @Environment(\.dismiss) private var dismiss

    init(title: String, message: String, initial: Int, range: ClosedRange<Int>, presets: [Int], onConfirm: @escaping (Int) -> Void) {
        self.title = title
        self.message = message
        self.range = range
        self.presets = presets
        self.onConfirm = onConfirm
        _value = State(initialValue: initial)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(AppFont.headlineSmall).foregroundStyle(AppColor.textPrimary)
            Spacer().frame(height: 6.dp)
            Text(message).font(AppFont.bodyMedium).foregroundStyle(AppColor.textSecondary)
            Spacer().frame(height: 20.dp)
            Text("\(value)분").font(AppFont.displayMedium).foregroundStyle(AppColor.textPrimary).frame(maxWidth: .infinity)
            Spacer().frame(height: 16.dp)
            HStack(spacing: 8.dp) {
                ForEach(presets, id: \.self) { preset in
                    ChoicePill(text: "\(preset)분", selected: value == preset) { value = preset }
                }
            }
            .frame(maxWidth: .infinity)
            Spacer().frame(height: 16.dp)
            Slider(value: Binding(get: { Double(value) }, set: { value = Int($0.rounded() / 5) * 5 }),
                   in: Double(range.lowerBound)...Double(range.upperBound), step: 5)
                .tint(AppColor.accent)
            Spacer().frame(height: 24.dp)
            PrimaryButton(title: "확인") {
                onConfirm(value)
                dismiss()
            }
        }
        .padding(.horizontal, Space.screen)
        .padding(.top, 28.dp)
        .padding(.bottom, 16.dp)
        .presentationDetents([.height(440.dp)])
        .presentationDragIndicator(.visible)
        .presentationBackground(AppColor.background)
    }
}

/// 하나 고르기 시트(안드로이드 ChoiceSheet): 라디오 점과 이름
struct ChoiceSheet<Value: Hashable>: View {
    let title: String
    let options: [(Value, String)]
    let selected: Value
    let onSelect: (Value) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(AppFont.headlineSmall).foregroundStyle(AppColor.textPrimary).padding(.bottom, 12.dp)
            ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                Button {
                    onSelect(option.0)
                    dismiss()
                } label: {
                    HStack(spacing: 14.dp) {
                        RadioDot(selected: option.0 == selected)
                        Text(option.1).font(AppFont.bodyLarge).foregroundStyle(AppColor.textPrimary)
                        Spacer()
                    }
                    .frame(minHeight: 52.dp)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, Space.screen)
        .padding(.top, 28.dp)
        .padding(.bottom, 16.dp)
        .presentationDetents([.height(CGFloat(options.count) * 52.dp + 120.dp)])
        .presentationDragIndicator(.visible)
        .presentationBackground(AppColor.background)
    }
}

/// 라디오 점(22dp): 선택되면 강조색 테두리 안에 점
struct RadioDot: View {
    let selected: Bool
    var body: some View {
        ZStack {
            Circle().strokeBorder(selected ? AppColor.accent : AppColor.dotted, lineWidth: 2.dp)
            if selected { Circle().fill(AppColor.accent).padding(6.dp) }
        }
        .frame(width: 22.dp, height: 22.dp)
    }
}
