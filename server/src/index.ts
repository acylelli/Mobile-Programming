import { createApp } from "./app.ts";

function requireEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    console.error(`${name} 환경변수가 없습니다. server/.env.example 을 참고해 .env 를 만들거나 배포 환경변수에 넣어 주세요.`);
    process.exit(1);
  }
  return value;
}

const app = createApp({
  odsayApiKey: requireEnv("ODSAY_API_KEY"),
  kakaoRestApiKey: requireEnv("KAKAO_REST_API_KEY"),
});

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => {
  console.log(`RouteAlarm proxy listening on :${port}`);
});
