# RouteAlarm — 목표 도착 시간 기반 스마트 출발 알람

> "몇 시까지 도착하고 싶은지"만 알려주면, 대중교통 상황을 분석해 **언제 일어나야 하는지**를 계산하고 알람을 자동으로 설정·조정하는 Android 앱

Kotlin · Jetpack Compose · Clean Architecture(MVVM) · Hilt · Room · DataStore · WorkManager · AlarmManager

화면은 팀 UI 설계서 v1.1(시안 C "Route Line", 기본 색 오렌지)의 화면 정의(UI-A-001 ~ UI-F-003)를 기준으로 구현했다.

---

## 목차

1. [서비스 소개](#1-서비스-소개)
2. [핵심 기능](#2-핵심-기능)
3. [화면과 스크린샷](#3-화면과-스크린샷)
4. [무료 플랜과 PRO](#4-무료-플랜과-pro)
5. [기술 스택](#5-기술-스택)
6. [아키텍처](#6-아키텍처)
7. [알람 계산 방식](#7-알람-계산-방식)
8. [교통 정보 갱신 방식](#8-교통-정보-갱신-방식)
9. [권한 설명](#9-권한-설명)
10. [프로젝트 구조](#10-프로젝트-구조)
11. [실행 방법](#11-실행-방법)
12. [API 설정 방법](#12-api-설정-방법)
13. [테스트 방법](#13-테스트-방법)
14. [주요 설계 결정](#14-주요-설계-결정)
15. [향후 개선사항](#15-향후-개선사항)

---

## 1. 서비스 소개

일반 알람 앱은 사용자가 직접 "몇 시에 일어날지"를 계산해야 한다. RouteAlarm 은 그 계산을 대신한다.

```
출발지   경기도 남양주시 다산동
도착지   한성대학교
목표 도착 09:00
준비 시간 30분

→ 현재 예상 이동 65분, 안전 여유 10분

목표 도착 09:00
권장 출발 07:45
기상 알람 07:15
```

교통 지연으로 예상 이동시간이 15분 늘어나면 앱이 다시 계산해 알람을 **06:55** 로 앞당긴다
(이동 +15분, 지연이 있을 때 더하는 여유 +5분). PRO 는 알람을 자동으로 옮기고
"교통 상황이 변경되어 알람이 20분 앞당겨졌어요. 07:15 → 06:55" 라고 알려주며,
무료 플랜은 같은 내용을 추천으로 보여주고 **[변경하기]** 를 눌렀을 때 반영한다.

## 2. 핵심 기능

| 기능 | 설명 |
|---|---|
| 자동 알람 계산 | 목표 도착 − 이동시간 − 안전 여유 − 준비시간 = 기상 알람 |
| 노선도형 홈 | 기상 → 출발 → 승차 → 환승 → 도착을 한 줄의 노선으로 보여준다. 노선색은 공식 노선색 고정 |
| 교통 상황 재확인 | PRO: 기상 24h / 3h / 1h / 20분 전에 WorkManager 로 재확인. 무료: 기상 30분 전 1회 |
| 안전한 자동 조정 | 5분 미만 변화는 무시, 30분 초과 변화는 사용자 확인. PRO 는 자동 조정, 무료 플랜과 자동 조정 OFF 는 변경 추천만 |
| 기상 알람 + 출발 알림 | 기상 알람(전체 화면, 알람음, 진동, 5·10분 스누즈)과 "10분 뒤 출발" / "지금 출발" 알림 분리 |
| 알람음·볼륨 | 기본 알람음·부드럽게·휴대폰 벨소리 직접 고르기·무음, 아이폰 설정 같은 볼륨 막대 10~100%(테마 강조색, 휴대폰 알람 볼륨과 관계없이 이 크기로 울리고 끝나면 원래 볼륨으로 복원), 30초에 걸쳐 점점 크게 울리기, 미리 듣기. 일정 상세에서 일정마다 다른 알람음·볼륨을 고를 수 있다(기본은 "설정 따르기". 등교는 크게, 주말 약속은 부드럽게) |
| Offline First | 네트워크 실패 시 Room 캐시 → 마지막 계산값으로 알람 유지, 화면에 안내 띠 표시 |
| 일정 만들기 5단계 | 출발지 → 도착지 → 도착 시각·반복 → 준비 시간 → 여유 시간, 저장 전에 계산 미리보기 |
| 아이폰식 휠 입력 | 도착 시각(오전/오후 · 시 · 분)과 준비 시간(시간 · 분)을 휠을 돌려 고른다. 시 휠이 11과 12 사이를 지나면 오전/오후가 따라 바뀐다 |
| 반복 일정 | 한 번(날짜 선택) / 평일 / 매일 / 요일 직접 선택 |
| 공휴일 건너뛰기 | 반복 알람은 공휴일·대체공휴일·선거일에 울리지 않고 다음 반복 요일로 넘어간다(일정마다 켜고 끄기). 상세 화면에 "12월 25일 (금) 성탄절에는 울리지 않아요"처럼 한 달 안의 건너뛸 날을 미리 알려 준다. 공휴일 표(2026~2027)는 우주항공청 월력요항 기준으로 앱에 들어 있어 오프라인에서도 동작한다 |
| 장소 관리 | 화면 안 검색, 즐겨찾기(집/학교/회사), 최근 검색, 현재 위치 출발 |
| 무료 플랜 / PRO | 무료는 일정 2개와 출발 전 교통 확인 1회, PRO 는 일정 무제한과 실시간 재계산 ([4장](#4-무료-플랜과-pro)) |
| 단체 이용권 | 학교·기업이 준 코드를 입력하면 기관·기간을 확인하고 PRO 적용 |
| 컬러 테마 | 기본(오렌지)·새벽·나이트 3종, 모든 플랜에서 선택 가능 |
| 재부팅·시간대 대응 | BOOT_COMPLETED / TIMEZONE_CHANGED 수신 후 알람 복구 |
| 테스트 도구 | 설정에서 교통 정보 즉시 재확인, 기상 알람 미리 울려보기 |
| 측정 지표 | API 호출/실패/캐시 적중/재계산/자동 조정 횟수를 Room 에 기록, 설정 화면에서 확인 |
| 학교 시간표 연동 (PRO) | 시간표 캡처 사진 인식(ML Kit 한국어, 기기 안에서 처리)·캘린더 반복 일정 가져오기·직접 입력 → 요일별 첫 수업에 맞춘 반복 알람 생성. 인식이 틀린 수업은 눌러서 수정·삭제, 학기가 바뀌어 다시 만들면 예전 시간표 알람을 새 알람으로 교체 |
| 경로 지도 | 일정 상세 화면에 카카오맵으로 경로 표시(탑승 구간은 노선색, 도보는 회색, 출발·도착 점). 상위 2개 경로는 ODsay 노선 그래픽으로 실제 도로·선로 모양대로 그린다. 네이티브 앱 키는 `local.properties` 에만 두고, 키가 없거나 인증에 실패하면 지도만 숨긴다 |
| 요일·시간대별 이동시간 학습 | 회차마다 출발 직전에 확인한 실시간 이동시간을 기록하고(같은 회차는 덮어써 마지막 값만 남김), 같은 요일·출발 시각 ±1시간 기록이 최근 8주에 3번 이상이면 다음 계산의 안정화 입력으로 쓴다. 월요일 아침만 늘 막히면 월요일 회차만 조금 더 일찍 깨운다(현재 ETA 보다 짧게 잡지는 않음). 상세 화면에 요일별 이동시간 막대와 "월요일은 평소보다 13분 더 걸려요" 안내 |
| 기상 습관 개인화 | 기상 알람을 끈 시각과 예정 시각의 차이(다시 울림 포함)를 최근 30일 모아, 평소 5분 이상 늦게 일어나면 "준비 시간 N분 늘리기"를 제안. 적용하면 출발 시각은 그대로 두고 기상 알람만 앞당긴다 |
| 홈 화면 위젯 | 다음 기상 알람 시각, 도착·출발 시각, 교통 상태(Jetpack Glance). 알람을 끄거나 다시 계산하면 바로 바뀐다 |
| 지각 위험 택시 안내 | 교통이 나빠졌거나 기상 알람을 늦게 꺼서 대중교통으로는 늦을 때 홈·상세 화면에 경고. 택시로 가면 몇 시 도착인지와 예상 요금(카카오모빌리티)을 보여 주고 카카오 T 를 출발·도착이 채워진 채로 연다 |
| 되돌리기 | 일정 삭제·알람 변경(추천 적용, 습관 제안 적용) 직후 "되돌리기" 스낵바로 바로 이전 상태로 복구 |
| 업데이트해도 데이터 유지 | Room 버전별 마이그레이션(파괴적 마이그레이션 없음). 모든 버전 사이에 Migration 이 있는지 단위 테스트가 검사 |
| 캘린더 연동 (PRO) | 기기 캘린더(Google·삼성 등)의 앞으로 2주 일정을 읽어, 한 번뿐인 일정은 도착지·시각을 채운 새 일정으로, 매주 반복 일정은 시간표로 가져오기 |

## 3. 화면과 스크린샷

| 화면 ID | 화면 | 구현 위치 (`presentation/…`) |
|---|---|---|
| UI-A-001 | 온보딩 3쪽 + 권한 안내 | `onboarding/OnboardingScreen.kt` |
| UI-B-001 | 홈 (내일 경로) | `home/HomeScreen.kt` |
| UI-C-001 ~ 005 | 새 일정 5단계 | `schedule/create/CreateScheduleScreen.kt`, `PlaceStep.kt` (시각·준비 시간 휠은 `core/designsystem/component/Wheel*.kt`) |
| UI-C-006 | 계산 미리보기 | `schedule/create/PreviewStep.kt` |
| UI-D-001 | 알람 목록 | `alarmlist/AlarmListScreen.kt` |
| UI-D-002 | 일정 상세 | `schedule/detail/ScheduleDetailScreen.kt` |
| UI-D-003 | 기상 알람 (전체 화면) | `alarm/service/AlarmRingingActivity.kt` |
| UI-D-004 | 출발·변경 알림 | `alarm/notification/AlarmNotifications.kt` |
| UI-E-001 | PRO 페이월 (개인 / 단체) | `subscription/PaywallSheet.kt` |
| UI-E-002 | 단체 이용권 코드 입력 | `subscription/GroupPassScreen.kt` |
| UI-F-001 | 설정 | `settings/SettingsScreen.kt` |
| UI-F-002 | 테마 선택 | `settings/theme/ThemeScreen.kt` |
| UI-F-003 | 구독 관리 | `subscription/SubscriptionScreen.kt` |
| UI-F-004 | 학교 시간표 | `timetable/TimetableScreen.kt` |
| UI-F-005 | 캘린더 연동 | `calendar/CalendarImportScreen.kt` |
| UI-G-001 | 홈 화면 위젯 | `widget/NextAlarmWidget.kt` |

에뮬레이터(Pixel 10 Pro, Android 17)에서 배포 서버의 실제 데이터로 캡처했다(다산역 → 한성대학교). 모든 사진은 720×1606 으로 크기를 맞췄다.

### 기본 흐름

<table>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/onboarding.png" width="200" alt="온보딩"><br><sub>온보딩</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/home.png" width="200" alt="홈 (노선도형 경로)"><br><sub>홈 (노선도형 경로)</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/create.png" width="200" alt="일정 만들기 — 장소"><br><sub>일정 만들기 — 장소</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/time.png" width="200" alt="도착 시각 휠"><br><sub>도착 시각 휠</sub></td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/preparation.png" width="200" alt="준비 시간 휠"><br><sub>준비 시간 휠</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/result.png" width="200" alt="계산 미리보기"><br><sub>계산 미리보기</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/alarms.png" width="200" alt="알람 목록"><br><sub>알람 목록</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/detail.png" width="200" alt="일정 상세"><br><sub>일정 상세</sub></td>
  </tr>
</table>

### 알람 상세 · 위젯

경로 지도는 실제 도로·선로 모양대로 그린 경로선이다. 요일별 이동시간 카드는 에뮬레이터 DB 에 4주치 시험용 이동시간 기록을 넣어 캡처했다. 기상 습관 카드와 택시 안내는 에뮬레이터 DB 에 시험용 기상 기록(알람을 늦게 끈 기록)을 넣어 캡처했고, 택시 시간·요금은 카카오모빌리티 실제 값이다.

<table>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/detail-map.png" width="200" alt="경로 지도 (카카오맵)"><br><sub>경로 지도 (카카오맵)</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/habit.png" width="200" alt="기상 습관 제안"><br><sub>기상 습관 제안</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/taxi.png" width="200" alt="지각 위험 → 택시 안내"><br><sub>지각 위험 → 택시 안내</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/undo.png" width="200" alt="삭제 후 되돌리기"><br><sub>삭제 후 되돌리기</sub></td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/holiday.png" width="200" alt="공휴일 건너뛰기"><br><sub>공휴일 건너뛰기</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/schedule-sound.png" width="200" alt="일정별 알람음·볼륨"><br><sub>일정별 알람음·볼륨</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/travel-pattern.png" width="200" alt="요일별 이동시간 학습"><br><sub>요일별 이동시간 학습</sub></td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/ringing.png" width="200" alt="기상 알람"><br><sub>기상 알람</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/widget.png" width="200" alt="홈 화면 위젯"><br><sub>홈 화면 위젯</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/settings.png" width="200" alt="설정 (연동 메뉴)"><br><sub>설정 (연동 메뉴)</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/theme.png" width="200" alt="테마 선택"><br><sub>테마 선택</sub></td>
  </tr>
</table>

### 학교 시간표 · 캘린더 연동 (PRO)

학교 시간표는 에브리타임 시간표 캡처(2026년 2학기) 한 장으로 테스트했다. 사진을 고르면 기기 안에서 글자를 읽어 요일별 수업으로 바꾸고, 틀린 곳은 눌러서 고친 뒤, 요일마다 첫 수업에 맞춘 알람을 만든다. 캘린더 화면의 일정은 에뮬레이터 캘린더에 넣은 시연용 일정이다.

<table>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/timetable.png" width="200" alt="시간표 사진 인식"><br><sub>시간표 사진 인식</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/timetable-edit.png" width="200" alt="수업 수정"><br><sub>수업 수정</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/timetable-alarms.png" width="200" alt="첫 수업 알람 만들기"><br><sub>첫 수업 알람 만들기</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/calendar.png" width="200" alt="캘린더 연동"><br><sub>캘린더 연동</sub></td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/calendar-prefill.png" width="200" alt="캘린더 일정 → 도착지 자동 검색"><br><sub>캘린더 일정 → 도착지 자동 검색</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/paywall.png" width="200" alt="PRO 페이월"><br><sub>PRO 페이월</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/group-pass.png" width="200" alt="단체 이용권"><br><sub>단체 이용권</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/subscription.png" width="200" alt="구독 관리"><br><sub>구독 관리</sub></td>
  </tr>
</table>

### 교통 변화 대응

자동 조정·변경 추천은 실제 교통 지연을 마음대로 만들 수 없어, 가짜 지연을 넣을 수 있던 이전 버전에서 캡처한 화면이다.

<table>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/auto-adjust.png" width="200" alt="교통 지연 → 자동 조정 (PRO)"><br><sub>교통 지연 → 자동 조정 (PRO)</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/suggestion.png" width="200" alt="교통 지연 → 변경 추천 (무료)"><br><sub>교통 지연 → 변경 추천 (무료)</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/offline.png" width="200" alt="네트워크 실패 → 마지막 계산 기준"><br><sub>네트워크 실패 → 마지막 계산 기준</sub></td>
  </tr>
</table>

### 테마별 화면

설정 > 테마에서 기본(오렌지)·새벽·나이트를 고를 수 있다. 강조색이 테마를 따라 바뀌어, 알람음 볼륨 막대도 오렌지 · 남색 · 연두색으로 칠해진다. 지하철 노선색은 테마와 관계없이 공식 색으로 고정된다.

<table>
  <tr>
    <th align="center">기본 (오렌지)</th>
    <th align="center">새벽</th>
    <th align="center">나이트</th>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/theme-default-home.png" width="200" alt="기본 (오렌지) — 홈"><br><sub>기본 (오렌지) — 홈</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/theme-dawn-home.png" width="200" alt="새벽 — 홈"><br><sub>새벽 — 홈</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/theme-night-home.png" width="200" alt="나이트 — 홈"><br><sub>나이트 — 홈</sub></td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="docs/screenshots/alarm-sound.png" width="200" alt="기본 (오렌지) — 알람음·볼륨"><br><sub>기본 (오렌지) — 알람음·볼륨</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/theme-dawn-sound.png" width="200" alt="새벽 — 알람음·볼륨"><br><sub>새벽 — 알람음·볼륨</sub></td>
    <td align="center" valign="top"><img src="docs/screenshots/theme-night-sound.png" width="200" alt="나이트 — 알람음·볼륨"><br><sub>나이트 — 알람음·볼륨</sub></td>
  </tr>
</table>

## 4. 무료 플랜과 PRO

| | 무료 | PRO (월 2,900원 / 연 24,000원, 기간제 이용권) |
|---|---|---|
| 일정 수 | 2개 | 무제한 |
| 교통 재확인 | 기상 30분 전 1회 | 기상 24h / 3h / 1h / 20분 전 |
| 교통이 바뀌었을 때 | 추천 카드와 알림의 **[변경하기]** 로 직접 반영 | 알람 자동 조정 + 알림 |
| 컬러 테마 | 3종 모두 | 3종 모두 |

- 플랜 판단은 화면이 아니라 도메인에서 한다: `CheckScheduleQuotaUseCase`(일정 한도), `RefreshCheckpointPolicy`(재확인 시점), `RefreshTransitTimeUseCase`(자동 조정 여부).
- 일정 한도에 걸리면 하루에 한 번만 페이월을 자동으로 띄우고, 같은 날 다시 누르면 안내 문구만 보여준다.
- **단체 이용권**: 코드 형식을 확인한 뒤 서버에 조회해 기관과 이용 기간을 보여주고 적용한다. 개인 구독 중에 적용하면 이중 결제가 되지 않도록 자동 갱신 해지를 안내한다.
- **결제는 토스페이먼츠 테스트 결제다.** 페이월의 "N원 결제하기"를 누르면 브라우저에서 토스 결제창이 열리고, 결제하면 앱으로 돌아와 서버가 승인한 주문만 PRO 로 적용된다. 테스트 키라 실제로 청구되지 않는다. 월간은 30일, 연간은 365일 동안 PRO 이며 자동 갱신은 없다. 구독 관리 화면의 "테스트: 무료 플랜으로 되돌리기"로 다시 시험할 수 있다.
- 단체 이용권 테스트 코드(서버가 관리): `HSU-2026-WAKE7`(한성대학교), `WAKE-2026-TEAM1`, 만료된 코드 `HSU-2025-PAST1`, 인원이 다 찬 코드 `HSU-2026-FULL0`

### UI 설계서와 다르게 구현한 부분

| 항목 | 설계서 v1.1 | 구현 | 이유 |
|---|---|---|---|
| 컬러 테마 | 5종(기본만 무료, 4종은 PRO 잠금) | 기본·새벽·나이트 3종, 모두 무료 | 모든 사용자가 테마를 쓸 수 있도록 잠금을 풀고, 인디고·모노를 빼서 3종으로 줄였다 |
| 화면 모드 | 라이트 / 다크 / 시스템 선택 | 선택 메뉴 없음. 기기의 다크 모드 설정을 따르고 나이트 테마는 항상 어두운 화면 | 테마만 고르도록 단순화했다 |
| "만약 15분 늦어진다면?" | 07:15 → 07:00 | 07:15 → 06:55 | 예시 숫자가 아니라 실제 계산 정책의 결과(이동 +15분, 지연 여유 +5분)를 보여준다 |
| 결제 | Google Play 정기 결제, 7일 무료체험 | 토스페이먼츠 테스트 결제(기간제 이용권, 자동 갱신·무료체험 없음) | Play Console 상품과 영수증 검증 서버 없이 실제 결제 흐름을 테스트하기 위함 |
| 시간표·캘린더 연동 | 학교 포털·Google 계정 연동 | 시간표 캡처 사진 인식·기기 캘린더 읽기·직접 입력 | 학교마다 다른 포털 로그인이나 Google OAuth 없이, 키·서버 없이 기기 안에서 처리하기 위함 |
| 광고 배너, 택시 안내 | 제공 | 미구현 | 외부 SDK·API 연동 범위 |

## 5. 기술 스택

| 분류 | 사용 기술 |
|---|---|
| 언어 | Kotlin 2.2, Coroutines / Flow |
| UI | Jetpack Compose, Material 3, Navigation Compose(타입 안전 라우트), 역할 기반 색 토큰(컬러 테마 3종 × 라이트/다크) |
| 아키텍처 | Clean Architecture, MVVM, UseCase, Repository Pattern |
| DI | Hilt (+ hilt-work, hilt-navigation-compose) |
| 저장 | Room (일정·장소·경로 캐시·알람 이력·지표), Preferences DataStore (사용자 설정·구독 상태·최근 검색) |
| 백그라운드 | WorkManager (교통 재확인), AlarmManager (정확한 알람), Foreground Service (알람 재생) |
| 네트워크 | Retrofit 3, OkHttp, kotlinx.serialization |
| 위치 | FusedLocationProviderClient (1회 조회만) |
| 테스트 | JUnit4, kotlinx-coroutines-test, Fake Repository |
| 빌드 | AGP 8.13, Gradle 9.2, KSP, Version Catalog |

## 6. 아키텍처

```
┌──────────────────────── presentation ────────────────────────┐
│ Compose Screen (Stateless)  ←  ViewModel (StateFlow<UiState>) │
└──────────────────────────────┬───────────────────────────────┘
                               │ UseCase 호출
┌──────────────────────────── domain ──────────────────────────┐
│ model (Schedule, AlarmPlan, TransitRoute …)                  │
│ usecase (BuildAlarmPlan, RefreshTransitTime, ScheduleAlarm …)│
│ repository 인터페이스, policy (AdjustmentPolicy …)            │
│   ※ Android 의존성 없음 → JVM 단위 테스트                     │
└──────────────────────────────┬───────────────────────────────┘
                               │ 인터페이스 구현
┌───────────────────────────── data ───────────────────────────┐
│ repository 구현 (Offline First)                               │
│ local: Room DAO, DataStore     remote: Proxy DataSource       │
│ (교통·장소·구독 각각 RemoteDataSource 인터페이스로 교체 가능) │
│ mapper: DTO·Entity ↔ Domain                                   │
└──────────────────────────────────────────────────────────────┘
┌───────────────────────────── alarm ──────────────────────────┐
│ AndroidAlarmScheduler(AlarmManager) · TransitRefreshWorker    │
│ AlarmReceiver · BootReceiver · AlarmService · Notifications   │
└──────────────────────────────────────────────────────────────┘
```

- UI 는 Retrofit/Room 을 직접 알지 못한다. `UI → ViewModel → UseCase → Repository 인터페이스 → 구현 → DataSource`.
- Repository 는 예외를 UI 로 던지지 않고 `AppResult<T>` / `AppError` 로 변환한다.
- AlarmManager·WorkManager·Notification 도 `AlarmScheduler`, `TransitRefreshScheduler`, `AlarmNotifier` 인터페이스 뒤에 숨겨 UseCase 를 JVM 에서 테스트한다.

## 7. 알람 계산 방식

`domain/usecase/BuildAlarmPlanUseCase.kt` 가 아래 UseCase 를 순서대로 조립한다.

```
travel    = EstimateTravelTime(현재 ETA, 최근 실시간 이동시간 n개)
            → max(현재, 0.7·현재 + 0.3·평균), 분 단위 올림
buffer    = CalculateSafetyBuffer(기본 여유, 지연, 실시간 여부)
            → 기본 + min(ceil(지연·0.3), 10) + (캐시 데이터면 +5)
departure = CalculateDepartureTime: target − travel − buffer
wakeUp    = CalculateWakeUpTime:   departure − preparation
```

- 모든 계산은 `Instant`(절대 시각) 위에서 한다. DST 전환일에 벽시계 시간으로 빼면 1시간 오차가 나기 때문이다.
- 권장 출발 시각이 이미 지났으면 "지금 출발" 기준으로 다시 계산해 `PlanOutcome.TooLate`(예상 도착·지각 분) 를 돌려준다.
- 반복 일정의 다음 회차는 `ResolveNextOccurrenceUseCase` 가 결정한다. 오늘 회차의 마지막 이벤트(출발 알림 또는 기상 알람)가 지나야 다음 회차로 넘어간다.

### 알람 조정 정책 (`domain/policy/AdjustmentPolicy.kt`)

| 변화량 | 동작 |
|---|---|
| 앞당김 < 5분 / 늦춤 < 10분 | 알람 유지 (ETA 노이즈 무시, 늦추는 쪽은 더 보수적) |
| 5~30분 | PRO: 자동 조정 + 알림. 무료 플랜이거나 자동 조정 OFF: 추천 카드와 알림의 [변경하기] |
| > 30분 | 사용자 확인 요청. 단 알람 1시간 이내로 남아 확인이 불가능하면 "앞당기기"만 자동 반영 (무료 플랜은 이때도 확인만 요청) |

## 8. 교통 정보 갱신 방식

```
일정 저장/활성화 ──▶ 즉시 1회 조회
                     │
                     ▼ ScheduleAlarmUseCase 가 다음 체크포인트 예약
        PRO : 기상 알람 24h 전 ──▶ 3h 전 ──▶ 1h 전 ──▶ 20분 전
        무료: 기상 알람 30분 전 1회                         (WorkManager, 네트워크 필요)
                     │
                     ▼ 각 체크포인트마다 RefreshTransitTimeUseCase
        경로 조회 → 새 계획 계산 → 조정 정책 판단 → Room 저장 → AlarmManager 재등록 → 알림
```

- WorkManager 는 정확한 시각을 보장하지 않으므로 "대략 그 즈음 재확인"만 담당하고, **실제 알람은 AlarmManager** 가 담당한다.
- 일정당 하루 최대 4회(무료 플랜은 1회)만 서버를 호출한다. 몇 분마다 폴링하지 않는다.
- **출발 직전 확인**: 출발 10분 전 알림과 출발 알림이 울릴 때 경로를 한 번 더 조회한다. 이때는 서버가 실시간 도착 정보를 붙여 주므로 알림에 "다산역 8호선 · 약 8분 후 도착"처럼 탈 차의 도착 안내가 함께 나온다. 기상 알람은 이미 울린 뒤라 알람 시각은 바꾸지 않고, 7초 안에 조회가 끝나지 않으면 마지막 계산값으로 알림을 보낸다("기상 + 출발 알림" 모드).
- 플랜이 바뀌면(체험 시작, 단체 이용권 적용, 무료 전환) `RestoreAlarmsUseCase` 로 체크포인트를 새 플랜 기준으로 다시 잡는다.
- 네트워크 실패 시: Room 경로 캐시(24h) → 그마저 없으면 마지막 계산값으로 알람 유지 + `isRealtime=false`. 홈과 상세에 "실시간 교통 정보를 가져오지 못했어요…" 안내 띠를 보여준다. Worker 는 백오프로 최대 3회 재시도.
- 재부팅/시간대 변경: `BootReceiver` → `RestoreAlarmsUseCase` 가 네트워크 없이 Room 의 마지막 이동시간으로 즉시 알람을 복구하고, 실시간 갱신은 다음 체크포인트에 맡긴다.

## 9. 권한 설명

| 권한 | 용도 | 없을 때 |
|---|---|---|
| `POST_NOTIFICATIONS` (13+) | 기상 알람 Foreground 알림, 출발/교통 변경 알림 | 알림 없이 알람 화면만 표시, 설정 화면에서 상태 안내 |
| `USE_EXACT_ALARM` (13+) / `SCHEDULE_EXACT_ALARM` (12) | 계산된 시각에 정확히 울리기 위해 | 출발 알림은 근사 알람으로 대체하고 사용자에게 안내 |
| `ACCESS_COARSE/FINE_LOCATION` | "현재 위치에서 출발" 선택 시 **조회 순간 1회** | 주소 검색으로 출발지 지정 |
| `READ_CALENDAR` | 캘린더 연동 화면을 열었을 때만 요청, 앞으로 2주 일정 읽기(쓰지 않음) | 시간표 사진·직접 입력으로 계속 |
| `RECEIVE_BOOT_COMPLETED` | 재부팅 후 알람 복구 | — |
| `USE_FULL_SCREEN_INTENT` | 잠금화면 위 알람 화면 | — |
| `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` | 알람음 재생 | — |

권한을 요청하기 전에 온보딩 마지막 쪽에서 **왜 필요한지**(알림·정확한 알람은 필수, 위치는 선택) 먼저 설명하고, 알림 → 정확한 알람 → 위치 순서로 요청한다. 거부해도 앱은 크래시 없이 동작하며 설정 화면에서 권한 상태를 확인할 수 있다.

## 10. 프로젝트 구조

```
app/src/main/java/com/example/routealarm
├── core
│   ├── common        AppResult / AppError / Dispatcher Qualifier
│   ├── designsystem  theme(AppColors 색 토큰, 컬러 테마 3종, Typography, Shapes)
│   │                 component(RouteLine 노선도, 아이폰식 휠 선택기, 버튼·카드·칩·시트 등 공통 컴포넌트)
│   └── util          TimeProvider, Duration 확장, 한글 줄바꿈 보정
├── domain            (Android 의존성 없음)
│   ├── model         Schedule, AlarmPlan, Place, TransitRoute, UserPreferences, Subscription …
│   ├── policy        AdjustmentPolicy, BufferPolicy, RefreshCheckpointPolicy, TimetablePolicy·TimetableImageParser, WakeUpHabitPolicy, LateRiskPolicy
│   ├── repository    인터페이스 (TransitRepository, AlarmScheduler …)
│   └── usecase       계산·갱신·알람 등록·일정 관리·구독(체험, 단체 이용권, 일정 한도) UseCase
├── data
│   ├── local/db      Room Entity, DAO, Database, Migrations(버전별 마이그레이션)
│   ├── timetable / calendar / habit  시간표(DataStore, ML Kit 글자 인식), 기기 캘린더, 기상 습관 기록
│   ├── local/datastore  Preferences DataStore
│   ├── remote        Transit / PlaceSearch / Subscription / Payment RemoteDataSource, dto, proxy/
│   ├── mapper        DTO·Entity ↔ Domain
│   ├── repository    Offline First 구현체
│   └── location      FusedLocationRepository
├── alarm
│   ├── scheduler     AndroidAlarmScheduler (AlarmManager)
│   ├── worker        TransitRefreshWorker (WorkManager)
│   ├── receiver      AlarmReceiver, BootReceiver
│   ├── service       AlarmService (Foreground), AlarmRingingActivity
│   └── notification  채널·알림 생성
├── presentation
│   ├── navigation    Route(타입 안전), RouteAlarmApp(NavHost + BottomBar)
│   ├── onboarding / home / alarmlist / schedule(create, detail — 경로 지도 RouteMap)
│   ├── timetable / calendar   학교 시간표, 캘린더 연동
│   ├── widget        홈 화면 위젯(Glance, NextAlarmWidget·WidgetUpdater)
│   ├── taxi          지각 위험 → 택시 안내 카드(LateRiskCard)
│   ├── settings(theme) / subscription(페이월, 단체 이용권, 구독 관리) / permission
│   └── common        노선도 화면 모델(RouteTimeline), 지도 경로선(RouteMapLines), 되돌리기(UndoManager), 노선색, 시간 포맷, 오류 메시지 매핑
└── di                Hilt 모듈 (App, Data, Network, Alarm)

app/src/test/java/com/example/routealarm
├── fake              테스트용 Fake Repository / Scheduler / Notifier
├── core              휠 선택기 계산 규칙과 움직임 단위 테스트
├── domain            계산·정책·갱신·구독 시나리오 단위 테스트
└── presentation      노선도·지도 경로선·위젯 다음 알람 단위 테스트

app/src/androidTest/java/com/example/routealarm
├── data/local/db     Room 마이그레이션·DAO 계측 테스트
├── alarm/worker      WorkManager Worker 테스트
└── presentation      Compose 화면 UI 테스트

server                Backend Proxy (Node.js) — ODsay 경로·Kakao 장소 검색을 앱 계약 형식으로 변환
├── src               app(라우팅·입력 검증), guard(요청 제한·하루 상한·캐시), odsay(경로·노선 그래픽), kakao, taxi(택시 예상), realtime(실시간 도착), payments·grouppass, contract(응답 형식)
└── test              변환 규칙·입력 검증 테스트 (실제 ODsay 응답 일부를 fixture 로 사용)
```

## 11. 실행 방법

1. Android Studio (Ladybug 이상, AGP 8.13 지원) 로 폴더를 연다.
2. `local.properties.example` 을 `local.properties` 로 복사하고 `sdk.dir` 을 맞춘다. (Android Studio 가 자동으로 만들어 주기도 한다)
3. 그대로 실행하면 배포 서버(`https://routealarm-proxy.onrender.com/`)의 실제 교통·장소·결제·단체 이용권 기능을 쓴다. 앱에는 API 키가 필요 없다.

```bash
./gradlew assembleDebug
./gradlew installDebug
```

- minSdk 26 / targetSdk 36 / JDK 17 이상
- 처음 실행하면 온보딩 3쪽(마지막 쪽에서 권한 안내) → 빈 홈 화면에서 **새 일정 만들기**로 시작한다.
- 이전 버전에서 업데이트하면 DB 구조가 바뀌어(버전 2) 저장된 일정이 초기화된다.

### 테스트 시나리오

1. 알람 탭의 **+** 로 일정을 만든다. 장소 검색은 Kakao, 경로·소요시간은 ODsay 실제 데이터다.
2. 출발이 30분 이내인 일정을 만들면 홈 노선도와 출발 알림에 "다산역 8호선 · 약 8분 후 도착" 같은 실시간 도착 안내가 붙는다. 설정 > 테스트 도구 > **지금 교통 정보 다시 확인** 으로 바로 다시 조회할 수 있다.
3. 설정 맨 위 PRO 배너 > **N원 결제하기** 로 토스페이먼츠 테스트 결제를 한다(실제 청구 없음). 결제가 끝나 앱으로 돌아오면 PRO 가 적용된다.
4. 설정 > **단체 이용권 코드 입력** 에 `HSU-2026-WAKE7` 을 넣으면 서버에서 기관·기간을 확인하고 PRO 가 적용된다.
5. 구독 관리 > **테스트: 무료 플랜으로 되돌리기** 로 결제·이용권을 다시 시험할 수 있다.
6. **기상 알람 미리 울려보기** 로 알람 화면을 바로 확인할 수 있다.

### 팀원에게 테스트 APK 배포 (Firebase App Distribution)

팀원은 Firebase App Tester 앱으로 설치하고, 새 버전이 올라오면 알림을 받아 바로 업데이트한다.

```bash
npx -y firebase-tools@15.36.0 login        # 처음 한 번, 브라우저에서 로그인
./scripts/distribute.sh "출시 노트"         # 빌드 → 테스터 그룹(기본 팀원)으로 배포
```

- `local.properties` 에 `routealarm.firebaseAppId`(Firebase 콘솔 > 프로젝트 설정 > 내 앱 > 앱 ID)를 넣는다. 그룹은 `routealarm.firebaseGroups` 로 바꿀 수 있다.
- 출시 노트를 생략하면 마지막 커밋 제목을 쓴다. 커밋하지 않은 변경이 있거나 `routealarm.proxyBaseUrl`(로컬 서버)이 켜져 있으면 배포하지 않는다.
- 버전 이름은 `0.2.<커밋 수>-<커밋 해시>` 라 콘솔에서 어떤 코드인지 바로 알 수 있다.
- 디버그 APK 는 빌드한 컴퓨터의 키로 서명된다. 테스터가 업데이트를 받으려면 항상 같은 컴퓨터에서 빌드해야 한다(카카오맵 키 해시도 그 컴퓨터 기준).

## 12. API 설정 방법

실제 교통 API 는 **Backend Proxy** 를 통해 사용한다. 앱에는 어떤 Secret 도 들어가지 않는다.

```properties
# local.properties (git 에 커밋되지 않음). 생략하면 배포 서버 https://routealarm-proxy.onrender.com/ 을 쓴다
routealarm.proxyBaseUrl=https://routealarm-proxy.onrender.com/
# 상세 화면 경로 지도(카카오맵) 네이티브 앱 키. 없으면 지도만 숨긴다.
routealarm.kakaoNativeAppKey=<카카오 네이티브 앱 키>
```

카카오맵은 콘솔의 네이티브 앱 키에 패키지명 `com.example.routealarm` 과 빌드하는 컴퓨터의 키 해시를 등록해야 인증된다(디버그 키 해시: `keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android | openssl sha1 -binary | openssl base64`).

Proxy 가 구현해야 하는 응답 형식은 [docs/proxy-api.md](docs/proxy-api.md) 에 있다.
ODsay·TMAP·공공데이터 등 어떤 제공자를 쓰든 Proxy 에서 이 형식으로 정규화하면 앱 코드는 바뀌지 않는다.

이 저장소의 [`server/`](server/README.md) 가 그 Proxy 구현이다(ODsay 경로, Kakao 장소 검색, 실시간 도착, 토스페이먼츠 테스트 결제, 단체 이용권).
키는 `server/.env` 나 배포 서비스 환경변수에만 두고, 에뮬레이터에서 로컬 서버에 붙을 때는 다음처럼 설정한다.

```properties
routealarm.proxyBaseUrl=http://10.0.2.2:8080/
```

배포 서버: **https://routealarm-proxy.onrender.com** (Render 무료 인스턴스, 싱가포르). 배포 절차는 [server/README.md 배포](server/README.md#배포-render) 에 있다.

제공자를 앱 안에서 직접 구현하고 싶다면 `TransitRemoteDataSource` 를 구현하고 `di/NetworkModule.kt` 에서 바인딩만 바꾸면 된다.

## 13. 테스트 방법

```bash
./gradlew testDebugUnitTest
```

도메인 계층과 화면 모델, 휠 선택기의 계산 규칙과 움직임은 Android 의존성이 없어 JVM 에서 바로 실행된다. 현재 180개 테스트:

| 테스트 | 검증 내용 |
|---|---|
| `BuildAlarmPlanUseCaseTest` | 09:00/60분/10분/30분 → 07:20, 자정 넘김, 다음날, DST, 이동 0분, 음수 입력, 과거 목표, 지각(TooLate), 캐시 데이터 여유 |
| `CalculationUseCasesTest` | 여유시간 계산, 이동시간 안정화(가중 평균·올림), 출발/기상 계산 |
| `EvaluateAlarmAdjustmentUseCaseTest` | 5분 미만 유지, 5분 이상 자동, 늦춤 10분 기준, 30분 초과 확인, 자동 조정 OFF, 알람 직전 예외 |
| `ResolveNextOccurrenceUseCaseTest` | 반복 요일의 다음 회차, 1회 일정 날짜 결정 |
| `RefreshCheckpointPolicyTest` | PRO 24h/3h/1h/20분 체크포인트 선택, 무료 플랜 30분 전 1회 |
| `RefreshTransitTimeUseCaseTest` | 교통 지연 → 자동 조정·알림, 작은 변화 유지, 추천/확인 요청, 추천 승인, 네트워크 실패 폴백, 알람 직전 즉시 울림, 이미 울린 알람은 변경하지 않음, 무료 플랜은 자동 조정 대신 추천, 실시간 재계산 때 회차별 실제 이동시간 기록(같은 회차는 덮어씀), 같은 요일 기록 3번이면 60분 → 66분으로 섞어 더 일찍 깨움, 다른 요일 기록은 쓰지 않음 |
| `SyncScheduleUseCaseTest` | 재부팅 복구, 회차 넘김, 1회 일정 종료, 시간대 변경, 정확한 알람 권한 없음, 스누즈 보존, 공휴일 건너뛰기를 끄고 켤 때 대체공휴일 회차 다시 맞춤 |
| `SubscriptionUseCasesTest` | 연간 요금(월 2,000원꼴·31% 할인), 무료 일정 한도, 서버가 승인한 결제만 PRO 적용·기간 만료, PRO 전환 시 재확인 재예약, 구독 종료 후 무료 전환, 단체 이용권 코드 정규화·형식 검사·확인·적용, 자동 갱신 구독 중 적용 시에만 해지 안내, 검증 서버 연결 실패 시 플랜 유지 |
| `PreviewDelayScenarioUseCaseTest` | "만약 15분 늦어진다면?" 카드: 이동 +15분·지연 여유 +5분, 실제 재계산과 같은 시각, 지연이 없으면 그대로 |
| `RouteTimelineBuilderTest` | 설계서 예시 경로의 지점·시각·구간, 승차 대기 시간 반영, 걷지 않고 바로 타는 경로, 경로 정보가 없는 계획, 방향 조사(로/으로) |
| `RouteMapLinesTest` | 지도 경로선: 출발지·환승역·도착지를 잇는 도보선과 정류장 좌표대로의 탑승 구간, 좌표 없는 예전 경로는 지도 없음 |
| `LateRiskPolicyTest` | 지각 위험: 제때 일어남·이동 중에는 경고 없음, 늦게 끈 알람 + 준비 시간으로 지각 계산, 출발 전 교통 악화, 3시간 범위, 택시 도착 예상 |
| `WakeUpHabitPolicyTest` | 기상 습관: 끈 시각 − 예정 시각의 중앙값, 한 번 크게 늦은 날·30일 지난 기록·비정상 값 제외, 3번 미만이면 제안 없음, 적용·넘김 뒤 기록만 다시 모음, 최대 30분 |
| `WakeUpHabitUseCasesTest` | 습관 제안 적용 시 같은 회차 기상 알람이 07:15 → 07:00 으로 앞당겨져 다시 등록되고 제안이 사라짐 |
| `RestoreScheduleUseCaseTest` | 되돌리기: 지운 일정이 같은 id·같은 계획으로 돌아오고 기상 알람 재등록, 바꾼 알람은 이전 기상 시각으로 복구 |
| `AlarmVolumePolicyTest` | 알람 볼륨: 비율 → 알람 볼륨 단계 변환(무음·기기 최소값 아래로 내려가지 않음), 점점 크게 10% → 30초 뒤 100%, 고른 벨소리가 없으면 기본 알람음, 일정 알람음이 설정의 소리·볼륨을 덮어쓰고 점점 크게는 설정을 따름 |
| `HolidayTest` | 공휴일 표(대체공휴일·선거일), 평일 알람이 성탄절·설 연휴(대체공휴일 포함)를 건너뛰고 다음 평일로, 매주 금요일 알람이 공휴일 두 번을 건너뜀, 끄면 공휴일에도 울림, 곧 건너뛸 공휴일 안내 |
| `TravelPatternPolicyTest` | 같은 요일·출발 ±1시간 기록만 같은 시간대로 봄, 저녁 출발·3번 미만·8주 지난 기록 제외, 요일별 중앙값과 평소보다 오래 걸리는 요일 찾기 |
| `NextAlarmTest` | 위젯의 다음 알람: 켜져 있고 출발 전인 일정 중 기상 알람이 가장 이른 것 |
| `MigrationCoverageTest` | 내보낸 Room 스키마(`app/schemas`)의 버전이 1부터 빠짐없이 이어지고, 이웃한 모든 버전 사이에 Migration 이 있음 |
| `TimetableTest` | 실제 에브리타임 시간표 캡처의 글자 위치와 ML Kit 실제 인식 결과(요일·시각 일부 누락, 글자 붙음)에서 같은 수업 8개 읽기, 12시간제 시각 변환, 요일별 첫 수업 알람 제안과 같은 시각 요일 묶기, 다시 만들 때 예전 알람 교체(일부 실패 시 남김) |
| `WheelMathTest` | 고리 휠의 칸 번호, 12시간제 숫자와 오전/오후(11↔12 전환, 자정·정오), 분이 시를 바꾸지 않음, 시간·분 길이 변환, 내가 알린 값과 바깥에서 온 값 구분, 손을 뗀 뒤 멈출 칸과 관성 거리 조정, 원통 모양(칸 위치·크기·진하기)과 누른 칸 찾기 |
| `WheelStateTest` | 휠의 움직임을 프레임 단위로 검증: 끌기 방향과 끝 저항, 튕긴 뒤 미리 정한 칸에 정확히 멈춤, 도는 휠을 잡아 멈추기, 잡고 있는 휠은 옮기지 않음, 바깥 값이 관성보다 우선, 취소된 움직임이 칸 사이에 남는 경우, 도는 도중의 오전/오후 전환 |

리포트: `app/build/reports/tests/testDebugUnitTest/index.html`

DB·Worker·화면은 실제 Android 런타임이 필요해 에뮬레이터에서 계측 테스트로 확인한다(CI 는 에뮬레이터가 없어 실행하지 않고, 테스트 코드가 컴파일되는지만 확인한다). 현재 21개:

```bash
./gradlew connectedDebugAndroidTest
```

| 계측 테스트 | 검증 내용 |
|---|---|
| `RouteAlarmMigrationTest` | 버전 1 DB 를 만들어 마이그레이션한 뒤 Room 스키마 검증, 2→3 공휴일 건너뛰기 열이 켜진 상태로 추가됨, 3→4 일정 알람음 열이 비어 있는(설정 따르기) 상태로 추가됨, 4→5 회차별 이동시간 표 추가(같은 회차는 덮어씀), 사용자 장소·일정·알람 기록 유지와 예시 데이터 삭제, 앱과 같은 설정으로 Room 이 열려 일정을 읽음 |
| `RouteAlarmDaoTest` | 메모리 SQLite: 일정·계획·경로 좌표가 Entity 를 거쳐 그대로 돌아옴, 켜진 일정만 조회, 일정 삭제 시 알람 기록 함께 삭제, 실시간 이동시간만 안정화 입력, 일정별 최근 조정, 기상 알람 끈 기록, 오래된 기록 정리 |
| `TransitRefreshWorkerTest` | `TestListenableWorkerBuilder` 로 Worker 실행: 재계산 후 기상 알람 등록·지표 기록, 네트워크 실패 시 재시도 후 포기, 일정 id 없으면 실패 |
| `ScreenUiTest` | Compose UI: 시간표 빈 화면·요일별 수업·알람 만들기·수업 수정, 모두 지우기 확인 창, 캘린더 권한 요청·반복 일정 추가·일정 선택, 알람 변경 추천 카드 |

> 기기에서 계측 테스트를 돌리면 테스트가 끝난 뒤 앱이 지워진다(앱 데이터도 함께). 실제로 쓰는 기기 말고 에뮬레이터에서 돌린다.

Proxy 서버 테스트는 `cd server && npm test` 로 실행한다(외부 API 를 호출하지 않아 키가 필요 없다).

### CI

GitHub Actions([`.github/workflows/ci.yml`](.github/workflows/ci.yml))가 `main` 푸시와 모든 PR 에서 두 작업을 돌린다.

- **Android**: `assembleDebug`, `assembleDebugAndroidTest`(계측 테스트 컴파일), `testDebugUnitTest`, `lintDebug` (JDK 17, 외부 API 를 호출하지 않아 키 불필요). 실패하면 테스트·Lint 리포트를 artifact 로 올린다.
- **Proxy 서버**: `npm ci`, 타입 검사, `npm test` (Node 20)

## 14. 주요 설계 결정

각 결정의 이유는 해당 소스의 KDoc 에도 적혀 있다.

| 결정 | 이유 |
|---|---|
| **Schedule 을 중심 개념으로, 알람 시각은 계산 결과(AlarmPlan)** | 나중에 시간표/캘린더 연동 시 Schedule 만 만들면 계산·알람·갱신 파이프라인을 재사용 (`domain/model/Schedule.kt`) |
| **Instant 기반 계산, 표시만 ZoneId 변환** | DST/시간대 변경 시 벽시계 계산 오차 방지 (`CalculateDepartureTimeUseCase`) |
| **TimeProvider.zone() 을 매번 읽음** | `Clock.systemDefaultZone()` 은 생성 시점 Zone 을 붙잡아 여행 중 시간대 변경을 놓침 (`core/util/TimeProvider.kt`) |
| **비대칭 히스테리시스** | 앞당김은 5분, 늦춤은 10분부터 반영. 늦잠 위험 쪽을 더 보수적으로 (`AdjustmentPolicy`) |
| **30분 초과는 자동 변경 금지, 단 알람 직전은 예외** | API 오류 가능성 vs 사용자가 자고 있어 확인 불가 → 지각 방지 우선 (`EvaluateAlarmAdjustmentUseCase`) |
| **이미 울린 기상 알람은 재계산해도 바꾸지 않음** | 사용자는 이미 깨어 있으므로 예상 도착만 갱신. 울리기 전이라면 "지금 당장" 알람으로 당긴다 (`RefreshTransitTimeUseCase`) |
| **캐시 경로로 계산한 조정은 다른 문구로 알림** | "혼잡해요"가 아니라 "실시간 정보를 못 가져와 여유를 더했어요"로 원인을 정확히 전달 (`AlarmNotifications`) |
| **이동시간은 현재 ETA 보다 짧아지지 않게 블렌딩** | 우연히 짧게 나온 ETA 하나로 알람이 늦어지는 것을 방지 (`EstimateTravelTimeUseCase`) |
| **일정의 장소는 FK 가 아닌 스냅샷** | 즐겨찾기 삭제/수정이 이미 설정된 알람을 조용히 바꾸지 않도록 (`ScheduleEntity`) |
| **setAlarmClock 으로 기상 알람 등록** | Doze 에서도 정확, 상태바 알람 아이콘, 13+ 에서 USE_EXACT_ALARM 으로 권한 프롬프트 불필요 (`AndroidAlarmScheduler`) |
| **알람 재생은 Foreground Service, 화면은 상태만 표시** | 화면이 꺼지거나 Activity 가 종료돼도 알람이 계속 울려야 함 (`AlarmService`) |
| **WorkManager 는 재확인만, AlarmManager 가 실제 알람** | WorkManager 는 실행 시각을 보장하지 않음 (`TransitRefreshWorker`) |
| **ScheduleAlarmUseCase 는 멱등(취소 후 재등록)** | 재부팅·재계산·시간대 변경 어디서든 안심하고 호출 가능 |
| **채널 3개 분리 (알람/출발/교통)** | 사용자가 교통 알림만 끄고 기상 알람은 유지할 수 있도록 |
| **좌표 캐시 키를 100m 단위로 반올림** | 정확한 위치를 저장하지 않으면서 현재 위치 흔들림에도 캐시 재사용 |
| **API Secret 은 Backend Proxy 에만** | APK 디컴파일 유출 방지 (`docs/proxy-api.md`) |
| **Dynamic Color 미사용** | 사용자가 고른 컬러 테마가 기기 배경화면에 따라 바뀌면 안 됨 (`core/designsystem/theme/Color.kt`) |
| **색은 역할 기반 토큰(AppColors)으로만 사용** | 컬러 테마를 바꿀 때 토큰 묶음만 교체하면 앱 전체에 반영됨. 노선색·상태색 같은 정보 색은 테마와 분리해 고정 |
| **플랜에 따른 차이는 도메인에서 판단** | 화면마다 PRO 여부를 따로 검사하면 빠뜨리기 쉬움. 일정 한도·재확인 시점·자동 조정을 UseCase/Policy 가 결정 (`SubscriptionUseCases.kt`, `RefreshCheckpointPolicy`) |
| **결제 결과는 앱이 아니라 서버가 판단** | 결제창에서 돌아오는 링크 값은 다른 앱도 만들 수 있으므로 믿지 않고, 서버가 금액을 확인해 승인한 주문만 PRO 로 적용한다 (`PaymentRemoteDataSource`, `server/src/payments.ts`) |
| **휠 선택기는 위치값 하나와 끌기 제스처로 직접 구현** | 고리처럼 끝없이 돌고, 원통 모양으로 그리고, 멈출 칸을 미리 정하려면 스크롤 목록보다 위치(가운데 칸 번호) 하나를 직접 움직이는 편이 단순하다. 끌기를 휠이 모두 가져가므로 스크롤 화면 안에서도 화면이 따라 움직이지 않는다 (`Wheel.kt`) |
| **시 휠은 24칸 고리, 오전/오후는 거기서 계산** | 12시간제 숫자를 두 번 반복해 보여주면 11↔12 를 지날 때 오전/오후가 저절로 바뀌고, 빠르게 돌려도 숫자와 오전/오후가 어긋날 수 없다 (`WheelMath.kt`) |
| **휠에서 손을 떼는 순간 멈출 칸을 정해 값을 알림** | 관성으로 도는 동안 다음 단계로 넘어가도 고른 값이 반영된다. 멈출 때까지 기다리면 그 사이의 값을 잃을 수 있다 (`WheelState.targetIndex`) |
| **입력 중인 글자는 Compose 상태로 두고 입력값을 바꿔 끼우지 않음** | StateFlow 를 거치거나 입력 도중 대문자로 바꾸면 빠르게 칠 때 글자가 빠짐. 대문자 변환은 표시와 서버 확인 단계에서만 (`GroupPassViewModel`) |

## 15. 향후 개선사항

- [x] 실제 Backend Proxy 서버 구현 (ODsay 경로·Kakao 장소 검색 정규화, `server/`)
- [x] Proxy 서버 https 배포 (Render, 수동 배포)
- [x] 출발 30분 이내 경로에 실시간 지하철·버스 도착 정보 반영 (`arrival_message`, 대기 시간)
- [x] 평소 대비 지연(`delay_minutes`) 계산: 실시간 대기 − 시간표 기준 대기
- [x] PRO 결제(토스페이먼츠 테스트 결제)와 서버의 단체 이용권 확인·사용
- [ ] 실제 출시용 결제(Google Play Billing 또는 토스페이먼츠 실 키·정기결제), 단체 이용권 발급·관리 화면
- [x] 지각 위험 시 택시 안내(택시 도착 예상·요금, 카카오 T 호출)
- [ ] 무료 플랜 배너 광고
- [x] 상세 화면 지도(카카오맵, 노선색 경로선, ODsay 노선 그래픽으로 실제 도로·선로 모양)
- [x] 학교 시간표(사진 인식·캘린더·직접 입력) / 기기 캘린더 가져오기 → Schedule 자동 생성
- [x] 알람 이력 기반 개인화: 기상 습관(알람을 늦게 끄는 시간) 학습 → 준비 시간 늘리기 제안
- [x] 요일·시간대별 실제 이동시간 학습(회차별 출발 직전 실시간 값 → 같은 요일·시간대 기록으로 보정)
- [ ] 서버 기반 실시간 모니터링 + FCM 푸시로 WorkManager 체크포인트 사이의 급격한 변화 대응
- [x] Compose UI 테스트, Room DAO 계측 테스트, WorkManager `TestListenableWorkerBuilder` 테스트
- [x] Room Migration(버전별 Migration, 누락 검사 단위 테스트, 마이그레이션 계측 테스트)
- [x] 알람 변경 되돌리기, 홈 화면 위젯
- [x] 알람음 고르기·볼륨·점점 크게, 일정별 알람음, 반복 알람 공휴일 건너뛰기
- [ ] Wear OS 전용 앱(타일·컴플리케이션). 지금은 휴대폰 기상 알람 알림이 워치로 전달되어 워치에서 끄기·다시 울림만 가능

---

이 프로젝트는 대학 프로젝트이자 Android 개발 포트폴리오로 제작되었다.
