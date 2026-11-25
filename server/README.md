# RouteAlarm Proxy 서버

앱이 직접 들고 있으면 안 되는 교통·장소 API 키를 대신 보관하고, 외부 API 응답을 앱 계약([docs/proxy-api.md](../docs/proxy-api.md)) 형식으로 바꿔 주는 서버다.

| 앱 엔드포인트 | 사용하는 외부 API |
|---|---|
| `GET /v1/transit/routes` | ODsay 대중교통 길찾기 (`searchPubTransPathT`) |
| `GET /v1/places/search` | Kakao 로컬 키워드 검색 |
| `GET /healthz` | 상태 확인 (배포 서비스 헬스체크용) |

## 실행

Node.js 20.6 이상이 필요하다.

```bash
cd server
npm install
cp .env.example .env   # 키를 채운다. .env 는 git 에 올라가지 않는다.
npm run dev            # http://localhost:8080
```

```bash
npm test         # 변환 로직·입력 검증·남용 방지 테스트 (외부 API 를 호출하지 않음)
npm run typecheck
```

## 앱과 연결

앱의 `local.properties` (git 에 올라가지 않음):

```properties
routealarm.transitProvider=proxy
# 에뮬레이터에서 개발 PC 의 서버에 접속할 때. 배포한 뒤에는 https 주소로 바꾼다. 끝의 / 는 꼭 붙인다.
routealarm.proxyBaseUrl=http://10.0.2.2:8080/
```

- http 접속은 debug 빌드에서 `10.0.2.2`, `localhost`, `127.0.0.1` 만 허용된다(`app/src/debug/res/xml/network_security_config.xml`). 실기기나 release 빌드는 https 로 배포한 주소를 쓴다.
- proxy 로 바꾸면 설정 > 데모의 **가상 교통 지연**, **네트워크 오류 시뮬레이션** 은 동작하지 않는다(fake 전용). 시연할 때는 `fake` 로 돌려 둔다.

## 배포 (Render)

운영 주소: **https://routealarm-proxy.onrender.com** (싱가포르, 무료 인스턴스). 앱의 `proxyBaseUrl` 기본값으로 들어가 있다.

### 지금 운영 방식

| 항목 | 방식 |
|---|---|
| 서비스 생성 | **New > Web Service > Public Git Repository** 에 저장소 주소를 넣어 만들었다(Root Directory `server`, Build `npm ci`, Start `npm start`, Health Check `/healthz`) |
| 배포 | **수동**. GitHub 와 연결하지 않은 공개 저장소 방식이라 `main` 에 병합해도 자동으로 배포되지 않는다 |
| 키 | Render 서비스의 **Environment** 에 `ODSAY_API_KEY`, `KAKAO_REST_API_KEY`, `NODE_VERSION=20` |
| 잠들기 방지 | [cron-job.org](https://cron-job.org) 가 10분마다 `/healthz` 를 호출한다 |

### `server/` 를 바꾼 뒤 배포하기

1. PR 을 `main` 에 병합한다(CI 통과 확인).
2. Render 의 `routealarm-proxy` 서비스 화면 오른쪽 위 **Manual Deploy > Deploy latest commit** 을 누른다.
3. 배포 상태가 **Live** 가 되면 `/healthz` 가 `{"ok":true}` 를 주는지, 바꾼 내용이 응답에 반영됐는지 확인한다.

앱 코드만 바뀐 경우에는 다시 배포할 필요가 없다.

### 처음부터 새로 만들 때

1. [Render](https://render.com) 에 가입하고 위 표대로 Web Service 를 만든다. 키는 이때 Environment 에 입력한다(저장소에는 키가 없다).
2. 서비스 화면 오른쪽 위 **Connect > Outbound** 에 있는 출구 IP 들을 ODsay LAB 의 서버 IP 로 모두 등록한다. 등록 전에는 경로 조회가 502 로 실패한다.
3. cron-job.org 에 `https://<서비스 이름>.onrender.com/healthz` 를 10분 간격으로 등록한다.

저장소 주인 계정으로 Render 를 GitHub 와 연결할 수 있다면 **New > Blueprint** 로 [`render.yaml`](../render.yaml) 을 쓰는 편이 낫다. 그러면 `main` 에서 CI 가 통과한 커밋이 자동 배포되고, 저장소 변수 `PROXY_HEALTHCHECK_URL` 을 넣으면 [`keep-alive`](../.github/workflows/keep-alive.yml) 워크플로가 cron-job.org 대신 서버를 깨운다. 지금은 둘 다 쓰지 않는다.

### 무료 인스턴스 참고

- 15분 동안 요청이 없으면 잠들고, 깨어나는 데 30초 이상 걸린다. 앱의 네트워크 제한시간(15초)보다 길어서 그 사이 요청은 캐시로 대체된다. 10분마다 깨우고 있어서 거의 생기지 않는다.
- 무료 사용 시간은 워크스페이스당 월 750시간이라, 이 서비스 하나를 24시간 켜 두는 정도까지 된다. 무료 서비스를 더 만들면 모자랄 수 있다.
- cron-job.org 는 계속 실패하는 작업을 자동으로 끈다. 실패 알림 메일을 받으면 서버 상태를 확인한다.

## 남용 방지

서버 주소는 앱에 들어 있고 저장소도 공개라서 누구나 호출할 수 있다. 외부 API 무료 한도를 지키려고 세 겹으로 막는다(`src/guard.ts`).

| 방어 | 기본값 | 환경변수 | 넘으면 |
|---|---|---|---|
| IP 별 경로 조회 | 1분에 20번 | `ROUTES_PER_MINUTE` | 429 + `Retry-After` |
| IP 별 장소 검색 | 1분에 60번 | `SEARCH_PER_MINUTE` | 429 + `Retry-After` |
| ODsay 하루 호출 | 1,000번 | `ODSAY_DAILY_LIMIT` | 429, 한국 시간 자정에 초기화 |
| Kakao 하루 호출 | 10,000번 | `KAKAO_DAILY_LIMIT` | 429, 한국 시간 자정에 초기화 |

- 같은 출발·도착(약 10m 단위) 경로는 1분, 같은 검색어(대소문자·공백 무시)는 10분 동안 캐시로 응답해서 외부 API 를 다시 부르지 않는다. 캐시 응답은 하루 상한에 들어가지 않는다.
- 하루 상한은 IP 를 바꿔 가며 호출해도 넘지 않는 전체 한도다. ODsay·Kakao 콘솔에서 확인한 실제 무료 한도보다 조금 낮게 맞춘다.
- IP 는 Render 앞단(Cloudflare)이 넣어 주는 `CF-Connecting-IP` 를 쓴다. 학교 Wi-Fi 처럼 여러 사람이 같은 IP 를 쓰면 함께 제한될 수 있어서 분당 한도를 넉넉하게 잡았다.
- 상태는 메모리에만 있어서 서버가 재시작하면 초기화된다(Render 무료 인스턴스는 한 대만 돈다).
- 앱은 429 를 다른 서버 오류와 똑같이 받아 경로는 24시간 이내 캐시로 대체하고, 장소 검색은 "검색 실패"로 안내한다.
- 앱에 고정 키를 넣는 방식은 쓰지 않는다. 저장소가 공개라 키가 그대로 보이고 APK 에서도 쉽게 꺼낼 수 있어서 막는 효과가 없다.

## 변환 규칙

- 경로는 총 소요시간이 짧은 순으로 최대 5개를 준다.
- 처음·마지막 도보는 `WALK` 구간, 탈것 사이의 도보는 `transfer_minutes` 로 합친다(앱 노선도가 환승을 그리는 방식에 맞춤).
- ODsay 총 소요시간과 구간 합의 차이는 `wait_minutes` 로 둔다. 그래서 항상 `총 = 도보 + 탑승 + 환승 + 대기` 이다.
- ODsay 는 출발 시각을 받지 않고 "지금 기준"으로 계산한다. `departure_time` 은 앱이 보낸 `depart_at`, `arrival_time` 은 출발 + 총 소요시간이다.
- `realtime` 은 "방금 조회한 값"이라는 뜻으로 `true` 를 준다(앱에서 `false` 는 캐시로 대체한 값을 뜻한다). `delay_minutes` 는 실시간 도착 API 를 붙이기 전까지 0 이다.
- 지하철 노선명은 "수도권 2호선" → "2호선", 역 이름은 "잠실" → "잠실역" 으로 맞춘다. 버스는 버스 번호에 "번"을 붙여 노선명으로 쓴다("76" → "76번").
- Kakao 가 노선별로 나눠 주는 역("잠실역 2호선", "잠실역 8호선")은 하나로 합치고 `lines` 에 노선을 모은다.
- 출발·도착이 700m 이내이거나 근처에 정류장이 없으면 빈 경로(`routes: []`)로 응답한다. 키 오류·한도 초과는 502, 외부 API 시간 초과는 504 다. 앱은 이때 24시간 이내 캐시로 대체한다.

## API 키 관리

- 키는 `.env`(로컬) 또는 배포 서비스의 환경변수에만 둔다. 저장소는 공개 상태이므로 키를 커밋하지 않는다.
- ODsay 서버용 키는 호출하는 서버의 IP 를 ODsay LAB 에 등록해야 동작한다. 팀원 PC 나 배포 서버에서 401/인증 실패가 나면 그 IP 가 등록됐는지 먼저 확인한다.
- 오류 응답에는 외부 API 원문을 싣지 않고 서버 로그에만 남긴다.
