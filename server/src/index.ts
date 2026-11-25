import { createApp, DEFAULT_LIMITS, type GuardLimits } from "./app.ts";

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
};

const app = createApp({
  odsayApiKey: requireEnv("ODSAY_API_KEY"),
  kakaoRestApiKey: requireEnv("KAKAO_REST_API_KEY"),
  limits,
});

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => {
  console.log(`RouteAlarm proxy listening on :${port}`, limits);
});
