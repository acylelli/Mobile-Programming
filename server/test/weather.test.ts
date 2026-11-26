import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { test } from "node:test";
import { createApp } from "../src/app.ts";
import { latestBase, parseForecast, toGrid, weatherAt } from "../src/weather.ts";

const kst = (iso: string) => Date.parse(`${iso}+09:00`);

test("위경도를 기상청 격자로 바꾼다(서울시청 60,127 · 부산 서면(부산진구) 98,75)", () => {
  assert.deepEqual(toGrid(37.5665, 126.978), { nx: 60, ny: 127 });
  assert.deepEqual(toGrid(35.1577, 129.0592), { nx: 98, ny: 75 });
});

test("발표 10분 뒤부터 조회할 수 있는 가장 최근 발표 시각을 고른다", () => {
  assert.deepEqual(latestBase(kst("2026-11-26T08:09:00")), { baseDate: "20261126", baseTime: "0500" });
  assert.deepEqual(latestBase(kst("2026-11-26T08:11:00")), { baseDate: "20261126", baseTime: "0800" });
  assert.deepEqual(latestBase(kst("2026-11-26T23:59:00")), { baseDate: "20261126", baseTime: "2300" });
  // 02:10 전에는 전날 23시 발표분
  assert.deepEqual(latestBase(kst("2026-11-27T01:30:00")), { baseDate: "20261126", baseTime: "2300" });
});

const item = (date: string, time: string, category: string, value: string) => ({ baseDate: "20261126", baseTime: "0500", category, fcstDate: date, fcstTime: time, fcstValue: value, nx: 60, ny: 127 });
const body = {
  response: {
    header: { resultCode: "00", resultMsg: "NORMAL_SERVICE" },
    body: {
      items: {
        item: [
          item("20261126", "0800", "PTY", "0"), item("20261126", "0800", "POP", "20"), item("20261126", "0800", "TMP", "3"),
          item("20261126", "0900", "PTY", "1"), item("20261126", "0900", "POP", "70"), item("20261126", "0900", "TMP", "4"),
          item("20261127", "0700", "PTY", "3"), item("20261127", "0700", "POP", "80"), item("20261127", "0700", "TMP", "-2"),
        ],
      },
    },
  },
};

test("출발 시각이 속한 1시간의 강수형태·강수확률·기온을 고른다", () => {
  const forecast = parseForecast(body)!;
  assert.deepEqual(weatherAt(forecast, kst("2026-11-26T09:40:00")), {
    forecast_at: "2026-11-26T09:00:00+09:00", precipitation: "RAIN", probability: 70, temperature: 4,
  });
  assert.equal(weatherAt(forecast, kst("2026-11-27T07:05:00"))!.precipitation, "SNOW");
  // 발표 직후라 그 시각 값이 없으면 3시간 안의 다음 값
  assert.equal(weatherAt(forecast, kst("2026-11-26T07:30:00"))!.forecast_at, "2026-11-26T08:00:00+09:00");
  // 예보 범위 밖
  assert.equal(weatherAt(forecast, kst("2026-12-03T08:00:00")), null);
});

test("결과 코드가 정상이 아니면 읽지 않는다", () => {
  assert.equal(parseForecast({ response: { header: { resultCode: "03", resultMsg: "NO_DATA" } } }), null);
});

test("GET /v1/weather 는 날씨를 주고, 같은 격자·발표분은 다시 부르지 않는다", async () => {
  let calls = 0;
  const fakeFetch: typeof fetch = async (input) => {
    const url = new URL(String(input));
    if (url.hostname === "apis.data.go.kr" && url.pathname.includes("VilageFcstInfoService_2.0")) {
      calls += 1;
      assert.equal(url.searchParams.get("nx"), "60");
      assert.equal(url.searchParams.get("base_time"), "0500");
      return Response.json(body);
    }
    throw new Error(`unexpected ${url}`);
  };
  const server = createApp({
    odsayApiKey: "k", kakaoRestApiKey: "k", realtimeKeys: { dataGoKrKey: "data-key" }, fetchImpl: fakeFetch,
    clock: () => kst("2026-11-26T07:00:00"),
  }).listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  try {
    const ok = await fetch(`${base}/v1/weather?lat=37.5665&lng=126.978&at=2026-11-26T09:30:00%2B09:00`);
    assert.equal(ok.status, 200);
    assert.equal(((await ok.json()) as { precipitation: string }).precipitation, "RAIN");
    await fetch(`${base}/v1/weather?lat=37.5665&lng=126.978&at=2026-11-26T08:10:00%2B09:00`);
    assert.equal(calls, 1);
    assert.equal((await fetch(`${base}/v1/weather?lat=37.5665&lng=126.978&at=2026-12-03T08:00:00%2B09:00`)).status, 404);
    assert.equal((await fetch(`${base}/v1/weather?lat=abc&lng=126.978&at=2026-11-26T09:30:00%2B09:00`)).status, 400);
  } finally {
    server.close();
  }
});
