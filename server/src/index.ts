import { readFileSync } from "node:fs";
import { createApp, DEFAULT_LIMITS, type GuardLimits } from "./app.ts";
import { parseGroupPasses } from "./grouppass.ts";

function requireEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    console.error(`${name} 환경변수가 없습니다. server/.env.example 을 참고해 .env 를 만들거나 배포 환경변수에 넣어 주세요.`);
    process.exit(1);
  }
  return value;
}

/** 비어 있으면 기본값. 숫자가 아니거나 1 미만이면 실수로 보고 서버를 띄우지 않는다. */
function limitEnv(name: string, fallback: number): number {
  const raw = process.env[name]?.trim();
  if (!raw) return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1) {
    console.error(`${name} 은 1 이상의 정수여야 합니다: ${raw}`);
    process.exit(1);
  }
  return value;
}

const limits: GuardLimits = {
  routesPerMinute: limitEnv("ROUTES_PER_MINUTE", DEFAULT_LIMITS.routesPerMinute),
  searchPerMinute: limitEnv("SEARCH_PER_MINUTE", DEFAULT_LIMITS.searchPerMinute),
  odsayDailyLimit: limitEnv("ODSAY_DAILY_LIMIT", DEFAULT_LIMITS.odsayDailyLimit),
  odsayPerIpDailyLimit: limitEnv("ODSAY_PER_IP_DAILY_LIMIT", DEFAULT_LIMITS.odsayPerIpDailyLimit),
  kakaoDailyLimit: limitEnv("KAKAO_DAILY_LIMIT", DEFAULT_LIMITS.kakaoDailyLimit),
  realtimeDailyLimit: limitEnv("REALTIME_DAILY_LIMIT", DEFAULT_LIMITS.realtimeDailyLimit),
  weatherDailyLimit: limitEnv("WEATHER_DAILY_LIMIT", DEFAULT_LIMITS.weatherDailyLimit),
};

/** 실시간 도착 키는 선택이다. 없으면 시간표 기준으로만 응답한다. */
const realtimeKeys = {
  seoulSubwayKey: process.env.SEOUL_SUBWAY_API_KEY?.trim() || undefined,
  dataGoKrKey: process.env.DATA_GO_KR_SERVICE_KEY?.trim() || undefined,
};

/** 토스페이먼츠 API 개별 연동 키(테스트). 없으면 결제 경로가 꺼진다. */
const tossKeys = {
  clientKey: process.env.TOSS_CLIENT_KEY?.trim() || undefined,
  secretKey: process.env.TOSS_SECRET_KEY?.trim() || undefined,
};

let groupPasses;
try {
  groupPasses = parseGroupPasses(process.env.GROUP_PASSES);
} catch (e) {
  console.error((e as Error).message);
  process.exit(1);
}

/**
 * 로컬 개발용 가짜 ODsay(ODSAY_MOCK=1). 실제 ODsay 대신 test/fixtures 의 실제 응답을 돌려줘
 * 내 컴퓨터에서 테스트하느라 배포 서버와 함께 쓰는 하루 1,000번 한도를 쓰지 않게 한다.
 * 출발·도착과 상관없이 같은 경로가 오고, 노선 모양은 없다(지도는 정류장 좌표로 그린다).
 */
const odsayMock = process.env.ODSAY_MOCK?.trim() === "1";
let fetchImpl: typeof fetch | undefined;
if (odsayMock) {
  const fixture = readFileSync(new URL("../test/fixtures/odsay-routes.json", import.meta.url), "utf8");
  fetchImpl = async (input, init) => {
    const url = String(input);
    if (url.startsWith("https://api.odsay.com/v1/api/searchPubTransPathT")) return new Response(fixture, { headers: { "content-type": "application/json" } });
    if (url.startsWith("https://api.odsay.com/v1/api/loadLane")) return Response.json({ result: { lane: [] } });
    return fetch(input, init);
  };
  console.log("[proxy] ODSAY_MOCK=1: 가짜 ODsay 응답을 씁니다(실제 ODsay 를 부르지 않음).");
}

const app = createApp({
  odsayApiKey: odsayMock ? "mock" : requireEnv("ODSAY_API_KEY"),
  fetchImpl,
  kakaoRestApiKey: requireEnv("KAKAO_REST_API_KEY"),
  limits,
  realtimeKeys,
  tossKeys,
  groupPasses,
});

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => {
  console.log(`RouteAlarm proxy listening on :${port}`, limits, {
    realtimeSubway: Boolean(realtimeKeys.seoulSubwayKey),
    realtimeBus: Boolean(realtimeKeys.dataGoKrKey),
    payments: Boolean(tossKeys.clientKey && tossKeys.secretKey),
    groupPasses: groupPasses.length,
  });
});
