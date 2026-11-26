import SwiftUI
import Shared
import UserNotifications

/// 설정: 알림 권한, 계산 방식 안내, 공용 모듈 정보
struct SettingsView: View {
    @State private var notificationsAllowed: Bool?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("설정").font(AppFont.headline).foregroundStyle(AppColor.textPrimary).padding(.top, 12)

                card {
                    row("알림", notificationsAllowed == false ? "꺼짐 — 설정에서 켜 주세요" : "켜짐", warn: notificationsAllowed == false)
                    if notificationsAllowed == false {
                        Button("iPhone 설정 열기") {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
                        }
                        .font(AppFont.label).foregroundStyle(AppColor.accent)
                    }
                    Text("기상 알람은 1분 간격으로 3번 더 울리고, 출발 시각에 한 번 더 알려요.")
                        .font(AppFont.caption).foregroundStyle(AppColor.textSecondary)
                }

                SectionLabel(text: "계산 방식")
                card {
                    Text("기상 = 도착 목표 − 이동 − 여유 − 준비").font(.app(17, .bold))
                    Text("이동시간은 실시간 경로와 최근 기록을 섞되 현재보다 짧게 잡지 않아요. 지연이 있으면 지연의 30%(최대 10분), 실시간이 아니면 5분, 비·소나기 5분, 눈 10분을 여유에 더해요.")
                        .font(AppFont.body).foregroundStyle(AppColor.textSecondary)
                    row("공휴일 표", "\(KoreanHolidays.shared.lastCoveredYear)년까지 반영")
                }

                SectionLabel(text: "앱 정보")
                card {
                    row("공용 계산 엔진", "Kotlin Multiplatform (shared)")
                    Text("안드로이드 앱과 같은 Kotlin 코드가 기상 시각·공휴일·날씨 여유·노선도를 계산해요. iOS 앱은 화면과 알림만 맡아요.")
                        .font(AppFont.caption).foregroundStyle(AppColor.textSecondary)
                    row("서버", "routealarm-proxy (Render)")
                    row("버전", Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-")
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(AppColor.background)
        .task {
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            notificationsAllowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12, content: content)
            .foregroundStyle(AppColor.textPrimary)
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 24).fill(AppColor.surface))
    }

    private func row(_ title: String, _ value: String, warn: Bool = false) -> some View {
        HStack {
            Text(title).font(.app(16))
            Spacer()
            Text(value).font(.app(15)).foregroundStyle(warn ? AppColor.danger : AppColor.textSecondary)
        }
    }
}
