# Backend Proxy API 계약

RouteAlarm 앱은 ODsay / TMAP / 공공데이터 등 실제 교통 API 를 **직접 호출하지 않는다**.
API Secret 을 APK 에 넣으면 디컴파일로 쉽게 유출되기 때문에, 앱은 아래 계약을 따르는 **Backend Proxy** 만 호출한다.
Proxy 서버는 환경변수로 실제 API 키를 보관하고, 제공자별 응답을 아래 형태로 정규화해 돌려준다.

앱 쪽 구현: `data/remote/proxy/ProxyApi.kt`, DTO: `data/remote/dto/TransitDtos.kt`

## 설정

`local.properties`

```properties
routealarm.transitProvider=proxy
routealarm.proxyBaseUrl=https://your-proxy.example.com/
```

`fake` 로 두면 `FakeTransitRemoteDataSource` 가 사용되어 API 키 없이 모든 기능을 시연할 수 있다.

## GET /v1/transit/routes

| query | 설명 |
|---|---|
| `origin_lat`, `origin_lng` | 출발 좌표 |
| `destination_lat`, `destination_lng` | 도착 좌표 |
| `depart_at` | ISO-8601 출발 희망 시각 (UTC, 예 `2026-10-05T22:45:00Z`) |

응답

```json
{
  "realtime": true,
  "routes": [
    {
      "total_minutes": 65,
      "walk_minutes": 15,
      "wait_minutes": 0,
      "transfer_minutes": 1,
      "transfer_count": 1,
      "delay_minutes": 0,
      "departure_time": "2026-10-06T07:45:00+09:00",
      "arrival_time": "2026-10-06T08:50:00+09:00",
      "segments": [
        { "mode": "WALK",   "from": "집",     "to": "다산역",       "minutes": 8 },
        { "mode": "SUBWAY", "line_name": "8호선", "from": "다산역", "to": "잠실역", "minutes": 31, "arrival_message": "3분 후 도착" },
        { "mode": "SUBWAY", "line_name": "2호선", "from": "잠실역", "to": "한성대입구역", "minutes": 18 },
        { "mode": "WALK",   "from": "한성대입구역", "to": "한성대학교", "minutes": 7 }
      ]
    }
  ]
}
```

- `total_minutes` = 도보 + 탑승 + 환승 + 대기 (지연 포함). 위 예시는 8 + 31 + 1 + 18 + 7 = 65분으로, 앱의 Fake 데이터와 UI 설계서 홈 화면 예시와 같다.
- `line_name` 은 홈 노선도의 구간 색을 정하는 데도 쓴다(예: "2호선" → 초록, "8호선" → 분홍). 수도권 지하철 노선명은 "N호선" 형태로 준다.
- `delay_minutes` 는 평소 대비 실시간 지연. 앱은 이 값으로 교통 상태 Chip 과 추가 여유시간을 계산한다.
- `realtime` 이 `false` 면 앱은 실시간 정보가 아니라는 안내를 표시하고 불확실성 여유(+5분)를 더한다.
- `mode` 는 `WALK | BUS | SUBWAY | TRAIN`, 그 외는 `ETC` 로 처리된다.
- 알 수 없는 필드는 무시된다(`ignoreUnknownKeys`). 필드를 추가해도 구버전 앱이 깨지지 않는다.

## GET /v1/places/search?query=…

```json
{
  "places": [
    { "name": "한성대학교", "address": "서울 성북구 삼선교로16길 116", "latitude": 37.5826, "longitude": 127.0105 },
    { "name": "잠실역", "address": "서울 송파구 올림픽로 지하 265", "latitude": 37.5133, "longitude": 127.1001, "lines": ["2호선", "8호선"] }
  ]
}
```

- `lines` (선택): 역·정류장을 지나는 노선. 검색 결과에 노선 배지로 표시한다. 없으면 생략하거나 빈 배열로 준다.

## 구독·단체 이용권 검증 (INR-003)

현재 앱은 `FakeSubscriptionRemoteDataSource` 만 연결되어 있어 실제 결제가 일어나지 않는다.
실제 서비스에서는 `SubscriptionRemoteDataSource` 구현이 Google Play Billing 으로 구매를 진행한 뒤
서버가 영수증을 검증해 아래 형태로 돌려주어야 한다. 앱은 영수증을 직접 판단하지 않고 서버 결과만 저장한다.
DTO: `data/remote/dto/SubscriptionDtos.kt`

구독 상태

```json
{
  "product_id": "routealarm_pro_yearly",
  "started_at": "2026-11-12T03:42:00Z",
  "trial_ends_at": "2026-11-19T03:42:00Z",
  "expires_at": "2026-11-19T03:42:00Z",
  "auto_renew": true
}
```

단체 이용권 확인·사용

```json
{
  "code": "HSU-2026-WAKE7",
  "organization": "한성대학교",
  "valid_from": "2026-03-01",
  "valid_until": "2027-02-28",
  "remaining_seats": 128
}
```

- 등록되지 않은 코드는 구현체가 `GroupPassNotFoundException` 을 던진다(HTTP 로 구현한다면 404 를 이 예외로 바꾼다). 앱은 형식(`영문·숫자 2~6자-숫자 4자-영문·숫자 4~8자`)이 맞는 코드만 서버에 보낸다.
- 기간이 지났거나 `remaining_seats` 가 0 이면 앱이 각각 만료·정원 초과로 안내한다.
- 기관에는 이용 인원만 집계해 제공하고, 개인 위치와 일정 정보는 서버로 보내지 않는다.

## 오류

- 5xx / 4xx → 앱은 `AppError.Server(code)` 로 변환하고 Room 캐시(24시간 이내)가 있으면 그것으로 대체한다.
- 네트워크 불가 → `AppError.Network`, 동일하게 캐시 폴백.
