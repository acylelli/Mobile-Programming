import SwiftUI
import UIKit

/// 안드로이드 Wheel 과 같은 모양의 휠: 아이폰 휠(반지름 88, 칸 32)에 화면 폭 전체의 회색 선택 띠(34dp),
/// 글자 24dp, 단위(시간·분)는 띠 안에 고정. 글자는 앱 글꼴(Roboto + Noto Sans KR)
struct WheelPicker: UIViewRepresentable {
    struct Column {
        var rows: [String]
        var width: CGFloat
        var alignment: NSTextAlignment
        var fontSize: CGFloat = 24
        /// 한 칸뿐인 고정 글자(단위)
        var isUnit: Bool { rows.count == 1 }
    }

    let columns: [Column]
    @Binding var selection: [Int]

    func makeUIView(context: Context) -> BandlessPicker {
        let picker = BandlessPicker()
        picker.dataSource = context.coordinator
        picker.delegate = context.coordinator
        picker.backgroundColor = .clear
        return picker
    }

    func updateUIView(_ picker: BandlessPicker, context: Context) {
        context.coordinator.parent = self
        picker.reloadAllComponents()
        for (component, row) in selection.enumerated() where component < columns.count {
            if picker.selectedRow(inComponent: component) != row { picker.selectRow(row, inComponent: component, animated: false) }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIPickerViewDataSource, UIPickerViewDelegate {
        var parent: WheelPicker
        init(_ parent: WheelPicker) { self.parent = parent }

        func numberOfComponents(in pickerView: UIPickerView) -> Int { parent.columns.count }
        func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int { parent.columns[component].rows.count }
        func pickerView(_ pickerView: UIPickerView, widthForComponent component: Int) -> CGFloat { parent.columns[component].width }
        func pickerView(_ pickerView: UIPickerView, rowHeightForComponent component: Int) -> CGFloat { 32.dp }

        func pickerView(_ pickerView: UIPickerView, viewForRow row: Int, forComponent component: Int, reusing view: UIView?) -> UIView {
            let column = parent.columns[component]
            let label = (view as? UILabel) ?? UILabel()
            label.text = column.rows[row]
            label.textAlignment = column.alignment
            label.font = AppFonts.uiFont(column.fontSize / AppFonts.textScale * Dp.scale, .regular)
            label.textColor = UIColor(AppColor.textPrimary)
            return label
        }

        func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
            var value = parent.selection
            while value.count < parent.columns.count { value.append(0) }
            value[component] = row
            parent.selection = value
        }
    }
}

/// 아이폰 휠의 기본 선택 띠를 지운 휠(띠는 SwiftUI 쪽에서 화면 폭 전체로 그린다)
final class BandlessPicker: UIPickerView {
    override func layoutSubviews() {
        super.layoutSubviews()
        for view in subviews where view.bounds.height < 60 { view.backgroundColor = .clear }
    }
}

/// 휠 틀: 높이 200dp, 가운데에 화면 폭 전체 회색 띠
private struct WheelFrame<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View {
        ZStack {
            Capsule().fill(AppColor.surfaceStrong).frame(height: 34.dp)
            content()
        }
        .frame(height: 200.dp)
        .frame(maxWidth: .infinity)
    }
}

/// 도착 시각 휠(안드로이드 WheelTimePicker): 오전/오후 · 시 · 분
struct WheelTimePicker: View {
    @Binding var hour: Int
    @Binding var minute: Int

    var body: some View {
        WheelFrame {
            WheelPicker(
                columns: [
                    .init(rows: ["오전", "오후"], width: 86.dp, alignment: .right),
                    .init(rows: (1...12).map(String.init), width: 60.dp, alignment: .right),
                    .init(rows: (0..<60).map { String(format: "%02d", $0) }, width: 86.dp, alignment: .center),
                ],
                selection: Binding(
                    get: { [hour >= 12 ? 1 : 0, (hour % 12 == 0 ? 12 : hour % 12) - 1, minute] },
                    set: { value in
                        let pm = value[0] == 1
                        let h12 = value[1] + 1
                        hour = (h12 % 12) + (pm ? 12 : 0)
                        minute = value[2]
                    }
                )
            )
            .frame(maxWidth: 300.dp)
        }
    }
}

/// 걸리는 시간 휠(안드로이드 WheelDurationPicker): 시간 · 분(5분 단위), 단위 글자는 띠 안에 고정
struct WheelDurationPicker: View {
    @Binding var minutes: Int
    let range: ClosedRange<Int>
    var step = 5

    var body: some View {
        let hourCount = range.upperBound / 60 + 1
        WheelFrame {
            WheelPicker(
                columns: [
                    .init(rows: (0..<hourCount).map(String.init), width: 56.dp, alignment: .right),
                    .init(rows: ["시간"], width: 44.dp, alignment: .left, fontSize: 17),
                    .init(rows: Array(stride(from: 0, to: 60, by: step)).map(String.init), width: 56.dp, alignment: .right),
                    .init(rows: ["분"], width: 44.dp, alignment: .left, fontSize: 17),
                ],
                selection: Binding(
                    get: { [minutes / 60, 0, minutes % 60 / step, 0] },
                    set: { value in
                        let total = value[0] * 60 + value[2] * step
                        minutes = min(max(total, range.lowerBound), range.upperBound)
                    }
                )
            )
            .frame(maxWidth: 300.dp)
        }
    }
}
