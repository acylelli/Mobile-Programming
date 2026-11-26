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

const app = createApp({
  odsayApiKey: requireEnv("ODSAY_API_KEY"),
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
