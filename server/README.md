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
npm test         # 변환 로직·입력 검증 테스트 (외부 API 를 호출하지 않음)
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

운영 주소: **https://routealarm-proxy.onrender.com** (싱가포르, 무료 인스턴스)

저장소 루트의 [`render.yaml`](../render.yaml) 이 배포 설정이다. 싱가포르 리전 무료 인스턴스로 `server/` 를 띄우고, `main` 에서 CI 가 통과한 커밋만 자동 배포한다.

1. [Render](https://render.com) 에 GitHub 계정으로 가입하고 **New > Blueprint** 에서 이 저장소를 고른다.
   저장소 주인이 아니라 Blueprint 로 저장소가 보이지 않으면 **New > Web Service > Public Git Repository** 에 저장소 주소를 넣고 Root Directory `server`, Build Command `npm ci`, Start Command `npm start`, Health Check Path `/healthz` 로 직접 만든다(지금 서비스는 이 방식으로 만들었다).
2. 처음 만들 때 `ODSAY_API_KEY`, `KAKAO_REST_API_KEY` 값을 입력한다(저장소에는 키가 없다).
3. 서비스 화면 오른쪽 위 **Connect > Outbound** 에 있는 출구 IP 들을 ODsay LAB 의 서버 IP 로 모두 등록한다. 등록 전에는 경로 조회가 502 로 실패한다.
4. 배포가 끝나면 `https://<서비스 이름>.onrender.com/healthz` 가 `{"ok":true}` 를 주는지 확인한다. 지금 운영 중인 주소는 `https://routealarm-proxy.onrender.com/` 이고 앱의 `proxyBaseUrl` 기본값으로 들어가 있다.
5. 잠들지 않게 하려면 저장소 **Settings > Secrets and variables > Actions > Variables** 에 `PROXY_HEALTHCHECK_URL`(위 `/healthz` 주소)을 넣는다. [`keep-alive`](../.github/workflows/keep-alive.yml) 워크플로가 10분마다 깨운다. 저장소 설정 권한이 없으면 cron-job.org 같은 외부 서비스로 같은 주소를 10분마다 호출해도 된다.

무료 인스턴스 참고:

- 15분 동안 요청이 없으면 잠들고, 깨어나는 데 30초 이상 걸린다. 앱의 네트워크 제한시간(15초)보다 길어서 그 사이 요청은 캐시로 대체된다. keep-alive 를 켜 두면 거의 생기지 않는다.
- 무료 사용 시간은 워크스페이스당 월 750시간이라, 이 서비스 하나를 24시간 켜 두는 정도까지 된다. 무료 서비스를 더 만들면 모자랄 수 있다.
- 예약 워크플로는 `main` 에 병합된 뒤부터 돌고, 저장소에 60일 동안 활동이 없으면 GitHub 가 멈춘다.

## 변환 규칙

- 경로는 총 소요시간이 짧은 순으로 최대 5개를 준다.
- 처음·마지막 도보는 `WALK` 구간, 탈것 사이의 도보는 `transfer_minutes` 로 합친다(앱 노선도가 환승을 그리는 방식에 맞춤).
- ODsay 총 소요시간과 구간 합의 차이는 `wait_minutes` 로 둔다. 그래서 항상 `총 = 도보 + 탑승 + 환승 + 대기` 이다.
- ODsay 는 출발 시각을 받지 않고 "지금 기준"으로 계산한다. `departure_time` 은 앱이 보낸 `depart_at`, `arrival_time` 은 출발 + 총 소요시간이다.
- `realtime` 은 "방금 조회한 값"이라는 뜻으로 `true` 를 준다(앱에서 `false` 는 캐시로 대체한 값을 뜻한다). `delay_minutes` 는 실시간 도착 API 를 붙이기 전까지 0 이다.
- 지하철 노선명은 "수도권 2호선" → "2호선", 역 이름은 "잠실" → "잠실역" 으로 맞춘다. 버스는 버스 번호를 노선명으로 쓴다.
- Kakao 가 노선별로 나눠 주는 역("잠실역 2호선", "잠실역 8호선")은 하나로 합치고 `lines` 에 노선을 모은다.
- 출발·도착이 700m 이내이거나 근처에 정류장이 없으면 빈 경로(`routes: []`)로 응답한다. 키 오류·한도 초과는 502, 외부 API 시간 초과는 504 다. 앱은 이때 24시간 이내 캐시로 대체한다.

## API 키 관리

- 키는 `.env`(로컬) 또는 배포 서비스의 환경변수에만 둔다. 저장소는 공개 상태이므로 키를 커밋하지 않는다.
- ODsay 서버용 키는 호출하는 서버의 IP 를 ODsay LAB 에 등록해야 동작한다. 팀원 PC 나 배포 서버에서 401/인증 실패가 나면 그 IP 가 등록됐는지 먼저 확인한다.
- 오류 응답에는 외부 API 원문을 싣지 않고 서버 로그에만 남긴다.
