# Backend Proxy API 계약

RouteAlarm 앱은 ODsay / TMAP / 공공데이터 등 실제 교통 API 를 **직접 호출하지 않는다**.
API Secret 을 APK 에 넣으면 디컴파일로 쉽게 유출되기 때문에, 앱은 아래 계약을 따르는 **Backend Proxy** 만 호출한다.
Proxy 서버는 환경변수로 실제 API 키를 보관하고, 제공자별 응답을 아래 형태로 정규화해 돌려준다.

앱 쪽 구현: `data/remote/proxy/ProxyApi.kt`, DTO: `data/remote/dto/TransitDtos.kt`

서버 구현: [`server/`](../server/README.md) (Node.js, ODsay 대중교통 길찾기 + Kakao 로컬 검색)

## 설정

`local.properties`

```properties
# 생략하면 배포 서버 https://routealarm-proxy.onrender.com/ 을 쓴다
# routealarm.proxyBaseUrl=https://routealarm-proxy.onrender.com/
```

에뮬레이터에서 개발 PC 의 서버(`server/`, `npm run dev`)에 붙을 때는 `routealarm.proxyBaseUrl=http://10.0.2.2:8080/` 을 쓴다. http 는 debug 빌드에서만 허용된다.

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

- `total_minutes` = 도보 + 탑승 + 환승 + 대기 (지연 포함). 위 예시는 8 + 31 + 1 + 18 + 7 = 65분으로, UI 설계서 홈 화면 예시와 같다.
- `line_name` 은 홈 노선도의 구간 색을 정하는 데도 쓴다(예: "2호선" → 초록, "8호선" → 분홍). 수도권 지하철 노선명은 "N호선" 형태로 준다.
- `wait_minutes` 는 첫 탑승까지의 대기. 출발이 30분 이내면 서버가 실시간 도착 정보로 다시 계산하고, 첫 탑승 구간에 `arrival_message`("7분 후 도착")를 붙인다. 실시간 목록 밖의 차를 배차 간격으로 추정한 경우에는 "약 10분 후 도착"처럼 "약"이 붙는다.
- `delay_minutes` 는 평소 대비 실시간 지연. 출발 30분 안이라 실시간 도착을 반영한 경로에서 "실시간 대기 − 시간표 기준 대기(ODsay)" 로 계산하고(더 빨리 오면 0), 그 밖에는 0 이다. `total_minutes` 에 이미 포함된 값이며, 앱은 이 값으로 교통 상태 Chip("지연 +N분")과 추가 여유시간을 계산한다.
- `realtime` 이 `false` 면 앱은 실시간 정보가 아니라는 안내를 표시하고 불확실성 여유(+5분)를 더한다. 방금 조회한 결과는 `true` 로 준다.
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

## PRO 결제 — 토스페이먼츠 테스트 결제 (INR-003)

앱은 결제 결과를 스스로 판단하지 않는다. 서버가 금액을 확인하고 토스페이먼츠에 승인을 요청한 결과만 저장한다.
테스트 키(`test_ck_`/`test_sk_`)를 쓰므로 실제로 청구되지 않는다. 기간제 이용권이며 자동 갱신은 없다.

| 상품 ID | 금액 | 이용 기간 |
|---|---|---|
| `routealarm_pro_monthly` | 2,900원 | 30일 |
| `routealarm_pro_yearly` | 24,000원 | 365일 |

1. 앱이 `POST /v1/payments/orders` (`{"product_id": "routealarm_pro_monthly"}`)로 주문을 만들고 `{"order_id", "checkout_url"}` 을 받는다. 주문 번호를 기억해 둔 뒤 `checkout_url`(`/pay/checkout?orderId=…`)을 브라우저로 연다. 서버가 토스 결제창을 띄우는 페이지를 준다.
   - 예전 앱은 `GET /pay/checkout?product={상품 ID}` 로 바로 열며, 이 경우 서버가 그 자리에서 주문을 만든다(계속 지원).
2. 결제하면 토스가 `GET /pay/success?paymentKey&orderId&amount` 로 돌려보낸다. 서버는 금액이 주문과 같은지 확인한 뒤 승인(`POST https://api.tosspayments.com/v1/payments/confirm`)한다. 취소·실패는 `GET /pay/fail` 로 온다.
3. 결과 페이지가 앱 딥링크 `routealarm://payment?orderId=…&result=…` 로 돌아간다.
4. 앱은 링크의 result 를 믿지 않고 `GET /v1/payments/{orderId}` 로 서버가 승인한 결과를 조회한다. 딥링크로 돌아오지 않고 브라우저를 닫았어도, 앱이 다시 열릴 때 기억해 둔 주문 번호로 같은 조회를 해 PRO 를 적용한다(주문은 24시간까지 확인).

```json
{
  "order_id": "ra_7d635003-f6da-4890-b1ac-8e973cb40101",
  "status": "PAID",
  "fail_code": null,
  "subscription": {
    "product_id": "routealarm_pro_monthly",
    "started_at": "2026-11-25T10:00:00.000Z",
    "expires_at": "2026-12-25T10:00:00.000Z",
    "auto_renew": false
  }
}
```

- `status`: `READY`(결제 전) | `PAID`(승인됨) | `FAILED`(취소·거절·금액 불일치). `subscription` 은 `PAID` 일 때만 있다.
- 주문은 서버 메모리에만 있어 재시작하면 진행 중인 주문은 사라진다(테스트 범위).
- DTO: `data/remote/dto/SubscriptionDtos.kt` 의 `PaymentResultDto`

## 단체 이용권 (SFR-016)

| 엔드포인트 | 설명 |
|---|---|
| `GET /v1/group-passes/{code}` | 기관·이용 기간·남은 인원 확인(사용하지 않음). 없는 코드는 404 |
| `POST /v1/group-passes/{code}/redeem` | 사용 처리(남은 인원 1 감소). 없는 코드 404, 만료·정원 초과 409 |

```json
{
  "code": "HSU-2026-WAKE7",
  "organization": "한성대학교",
  "valid_from": "2026-03-01",
  "valid_until": "2027-02-28",
  "remaining_seats": 128
}
```

- 앱은 형식(`영문·숫자 2~6자-숫자 4자-영문·숫자 4~8자`)이 맞는 코드만 서버에 보낸다. 404 는 "등록되지 않은 코드"로 안내한다.
- 기간이 지났거나 `remaining_seats` 가 0 이면 앱이 각각 만료·정원 초과로 안내한다.
- 테스트 코드: `HSU-2026-WAKE7`, `WAKE-2026-TEAM1`, 만료 `HSU-2025-PAST1`, 정원 초과 `HSU-2026-FULL0`. 서버 환경변수 `GROUP_PASSES` 로 바꿀 수 있다.
- 기관에는 이용 인원만 집계해 제공하고, 개인 위치와 일정 정보는 서버로 보내지 않는다.

## 오류

- 5xx / 4xx → 앱은 `AppError.Server(code)` 로 변환하고 Room 캐시(24시간 이내)가 있으면 그것으로 대체한다.
- 네트워크 불가 → `AppError.Network`, 동일하게 캐시 폴백.
- 429 → 요청이 너무 많거나(IP 별 분당 한도) 서버의 하루 외부 API 호출 상한에 도달한 경우. `Retry-After` 헤더에 다시 시도할 수 있을 때까지 남은 초를 준다. 앱은 다른 서버 오류와 똑같이 처리한다.
