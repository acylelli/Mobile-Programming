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
      "wait_minutes": 2,
      "transfer_minutes": 3,
      "transfer_count": 1,
      "delay_minutes": 0,
      "departure_time": "2026-10-06T07:45:00+09:00",
      "arrival_time": "2026-10-06T08:50:00+09:00",
      "segments": [
        { "mode": "WALK",   "from": "집",     "to": "다산역",       "minutes": 8 },
        { "mode": "SUBWAY", "line_name": "8호선", "from": "다산역", "to": "잠실역", "minutes": 30, "arrival_message": "3분 후 도착" },
        { "mode": "SUBWAY", "line_name": "2호선", "from": "잠실역", "to": "한성대입구역", "minutes": 15 },
        { "mode": "WALK",   "from": "한성대입구역", "to": "한성대학교", "minutes": 7 }
      ]
    }
  ]
}
```

- `total_minutes` = 도보 + 탑승 + 환승 + 대기 (지연 포함)
- `delay_minutes` 는 평소 대비 실시간 지연. 앱은 이 값으로 교통 상태 Chip 과 추가 여유시간을 계산한다.
- `realtime` 이 `false` 면 앱은 "마지막 계산 기준"으로 표시하고 불확실성 여유를 더한다.
- `mode` 는 `WALK | BUS | SUBWAY | TRAIN`, 그 외는 `ETC` 로 처리된다.
- 알 수 없는 필드는 무시된다(`ignoreUnknownKeys`). 필드를 추가해도 구버전 앱이 깨지지 않는다.

## GET /v1/places/search?query=…

```json
{
  "places": [
    { "name": "한성대학교", "address": "서울 성북구 삼선교로16길 116", "latitude": 37.5826, "longitude": 127.0105 }
  ]
}
```

## 오류

- 5xx / 4xx → 앱은 `AppError.Server(code)` 로 변환하고 Room 캐시(24시간 이내)가 있으면 그것으로 대체한다.
- 네트워크 불가 → `AppError.Network`, 동일하게 캐시 폴백.
