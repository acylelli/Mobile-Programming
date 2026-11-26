import SwiftUI
import UserNotifications

/// 온보딩 3쪽 + 권한 안내(안드로이드 UI-A-001 과 같은 구성·문구)
struct OnboardingView: View {
    let onFinish: () -> Void
    @State private var page = 0
    private let count = 3

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                HStack(spacing: 7.dp) {
                    ForEach(0..<count, id: \.self) { index in
                        Capsule().fill(index == page ? AppColor.textPrimary : AppColor.outline)
                            .frame(width: index == page ? 24.dp : 7.dp, height: 7.dp)
                    }
                }
                Spacer()
                Text("\(page + 1)/\(count)").font(AppFont.titleSmall).foregroundStyle(AppColor.textSecondary)
            }
            .padding(.horizontal, Space.screen)
            .padding(.top, 20.dp)

            TabView(selection: $page) {
                ForEach(0..<count, id: \.self) { index in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            Spacer().frame(height: 36.dp)
                            content(index)
                            Spacer().frame(height: Space.lg)
                        }
                        .padding(.horizontal, Space.screen)
                    }
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            PrimaryButton(title: page == count - 1 ? "권한 허용하고 시작하기" : "다음") {
                if page == count - 1 {
                    Task {
                        await AlarmNotifier.requestPermission()
                        onFinish()
                    }
                } else {
                    withAnimation { page += 1 }
                }
            }
            .padding(.horizontal, Space.screen)
            TextLink(text: page == count - 1 ? "나중에 설정할게요" : "건너뛰기") {
                if page == count - 1 { onFinish() } else { withAnimation { page = count - 1 } }
            }
            .padding(.bottom, 4.dp)
        }
        .background(AppColor.background)
    }

    @ViewBuilder
    private func content(_ index: Int) -> some View {
        switch index {
        case 0:
            intro("도착 시간만\n알려주세요", "몇 시까지 도착하고 싶은지만 정하면, 언제 일어나야 하는지는 직접 계산하지 않아도 돼요.")
            SurfaceCard {
                VStack(spacing: 14.dp) {
                    sample("어디에서", "집")
                    sample("어디까지", "한성대학교")
                    HStack {
                        Text("몇 시까지").font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary)
                        Spacer()
                        Text("09:00 도착").font(AppFont.headlineMedium).foregroundStyle(AppColor.accentText)
                    }
                }
            }
        case 1:
            intro("교통 상황까지\n계산해요", "도보, 대기, 탑승, 환승 시간을 더하고, 지연이 생기면 다시 계산해요.")
            SurfaceCard {
                VStack(alignment: .leading, spacing: 14.dp) {
                    HStack(spacing: 6.dp) {
                        Pill(text: "도보 15분", background: AppColor.background)
                        Pill(text: "8호선 31분", foreground: SampleLineColors.line8, background: SampleLineColors.line8.opacity(0.13))
                        Pill(text: "2호선 18분", foreground: SampleLineColors.line2, background: SampleLineColors.line2.opacity(0.13))
                    }
                    sample("대중교통 이동", "65분")
                    sample("여유", "10분")
                    sample("준비", "30분")
                }
            }
        default:
            intro("늦지 않도록\n깨워드릴게요", "도착 시각만 알려주시면, 교통 상황을 거꾸로 계산해 기상-출발 시각을 맞춰드려요.")
            SurfaceCard(padding: 0) {
                VStack(spacing: 8.dp) {
                    RouteStripView(
                        stops: [.init(isAlarm: true, color: AppColor.accent), .init(isAlarm: false, color: AppColor.textPrimary),
                                .init(isAlarm: false, color: SampleLineColors.line2), .init(isAlarm: true, color: AppColor.accent)],
                        links: [.init(color: AppColor.dotted, weight: 30), .init(color: SampleLineColors.line8, weight: 31),
                                .init(color: SampleLineColors.line2, weight: 34)]
                    )
                    HStack {
                        Text("07:15 기상"); Spacer(); Text("07:45 출발"); Spacer(); Text("08:50 도착")
                    }
                    .font(AppFont.labelMedium).foregroundStyle(AppColor.textPrimary)
                }
                .padding(.horizontal, 16.dp).padding(.vertical, 18.dp)
            }
            Spacer().frame(height: 28.dp)
            Text("서비스 이용을 위해 권한이 필요해요").font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
            Spacer().frame(height: 12.dp)
            VStack(spacing: 10.dp) {
                permission("bell", "알림", "알람과 출발 알림을 보내요", required: true)
                permission("alarm", "정확한 알람", "정해진 시각에 정확히 울려요", required: true)
                permission("location", "위치", "출발지를 현재 위치로 지정할 때만 1회 사용", required: false)
            }
        }
    }

    @ViewBuilder
    private func intro(_ title: String, _ body: String) -> some View {
        Text(title).font(.app(26 * 1.07, .heavy)).foregroundStyle(AppColor.textPrimary).fixedSize(horizontal: false, vertical: true)
        Spacer().frame(height: 12.dp)
        Text(body).font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary).fixedSize(horizontal: false, vertical: true)
        Spacer().frame(height: Space.lg)
    }

    private func sample(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(AppFont.bodyLarge).foregroundStyle(AppColor.textSecondary)
            Spacer()
            Text(value).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
        }
    }

    private func permission(_ icon: String, _ title: String, _ body: String, required: Bool) -> some View {
        OutlineCard {
            HStack(spacing: 14.dp) {
                Image(systemName: icon).font(.system(size: 20.dp)).foregroundStyle(AppColor.textPrimary)
                    .frame(width: 42.dp, height: 42.dp)
                    .background(RoundedRectangle(cornerRadius: Radius.tile).fill(AppColor.surface))
                VStack(alignment: .leading, spacing: 2.dp) {
                    HStack(spacing: 8.dp) {
                        Text(title).font(AppFont.titleMedium).foregroundStyle(AppColor.textPrimary)
                        if required {
                            TagBadge(text: "필수", foreground: AppColor.background, background: AppColor.textPrimary)
                        } else {
                            TagBadge(text: "선택")
                        }
                    }
                    Text(body).font(AppFont.bodySmall).foregroundStyle(AppColor.textSecondary)
                }
                Spacer(minLength: 0)
            }
        }
    }
}
