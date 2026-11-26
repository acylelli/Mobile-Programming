import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { test } from "node:test";
import { createApp, routeCacheKey } from "../src/app.ts";
import { KeyedDailyQuota } from "../src/guard.ts";
import type { OdsayResponse } from "../src/odsay.ts";

const odsayOk: OdsayResponse = {
  result: {
    path: [
      {
        info: { totalTime: 40 },
        subPath: [
          { trafficType: 3, sectionTime: 4 },
          { trafficType: 1, sectionTime: 34, startName: "다산", endName: "잠실", lane: [{ name: "수도권 8호선", subwayCode: 8 }], passStopList: { stations: [{ stationName: "다산" }, { stationName: "별내" }] } },
          { trafficType: 3, sectionTime: 2 },
        ],
      },
    ],
  },
};
const odsayQuotaError = { error: [{ code: "429", message: "Daily quota exceeded" }] };

const START = Date.parse("2026-11-26T01:00:00Z"); // 10:00 KST
const MIN = 60_000;

async function withServer(
  options: { odsayDailyLimit?: number; odsayPerIpDailyLimit?: number; odsay?: () => unknown },
  run: (ctx: { get: (path: string, ip?: string) => Promise<Response>; odsayCalls: () => number; advance: (ms: number) => void }) => Promise<void>,
) {
  let now = START;
  let odsayCalls = 0;
  const fakeFetch: typeof fetch = async (input) => {
    if (String(input).startsWith("https://api.odsay.com/")) {
      odsayCalls += 1;
      return Response.json(options.odsay ? options.odsay() : odsayOk);
    }
    throw new Error(`unexpected ${String(input)}`);
  };
  const server = createApp({
    odsayApiKey: "k",
    kakaoRestApiKey: "k",
    fetchImpl: fakeFetch,
    clock: () => now,
    limits: { odsayDailyLimit: options.odsayDailyLimit ?? 1000, odsayPerIpDailyLimit: options.odsayPerIpDailyLimit ?? 1000, routesPerMinute: 1000 },
  }).listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  try {
    await run({
      get: (path, ip) => fetch(`${base}${path}`, ip ? { headers: { "cf-connecting-ip": ip } } : undefined),
      odsayCalls: () => odsayCalls,
      advance: (ms) => { now += ms; },
    });
  } finally {
    server.close();
  }
}

const route = (lat: number) => `/v1/transit/routes?origin_lat=${lat}&origin_lng=127.15&destination_lat=37.51&destination_lng=127.10&depart_at=2026-11-26T22:00:00Z`;

test("같은 경로는 20분 동안 ODsay 를 다시 부르지 않는다(출발 시각이 달라도)", async () => {
  await withServer({}, async ({ get, odsayCalls, advance }) => {
    assert.equal((await get(route(37.62))).status, 200);
    advance(10 * MIN);
    const again = await get(route(37.62).replace("22:00", "23:30"));
    assert.equal(((await again.json()) as { realtime: boolean }).realtime, true);
    assert.equal(odsayCalls(), 1);
    advance(11 * MIN);
    await get(route(37.62));
    assert.equal(odsayCalls(), 2);
  });
});

test("하루 상한의 80%를 넘으면 절약 모드: 한 번 받은 경로는 예비 캐시로 답하고 realtime=false", async () => {
  await withServer({ odsayDailyLimit: 10 }, async ({ get, odsayCalls, advance }) => {
    for (let i = 0; i < 8; i++) await get(route(37.60 + i * 0.01)); // 8/10 사용
    assert.equal(odsayCalls(), 8);
    advance(30 * MIN); // 20분 캐시는 지났지만 예비 캐시는 남아 있다
    const saved = (await (await get(route(37.60))).json()) as { realtime: boolean; routes: unknown[] };
    assert.equal(saved.realtime, false);
    assert.equal(saved.routes.length, 1);
    assert.equal(odsayCalls(), 8);
    // 처음 보는 경로는 아직 상한 안이라 ODsay 에 묻는다
    assert.equal((await get(route(36.0))).status, 200);
    assert.equal(odsayCalls(), 9);

    const usage = (await (await get("/v1/usage")).json()) as { saver_mode: boolean; odsay: { used: number; limit: number } };
    assert.deepEqual(usage.odsay, { used: 9, limit: 10 });
    assert.equal(usage.saver_mode, true);
  });
});

test("하루 상한이 다 차면 예비 캐시가 있는 경로는 200(realtime=false), 없는 경로만 429", async () => {
  await withServer({ odsayDailyLimit: 2 }, async ({ get, advance }) => {
    await get(route(37.61));
    await get(route(37.62));
    advance(30 * MIN);
    const saved = await get(route(37.61));
    assert.equal(saved.status, 200);
    assert.equal(((await saved.json()) as { realtime: boolean }).realtime, false);
    const unknown = await get(route(36.5));
    assert.equal(unknown.status, 429);
    assert.ok(Number(unknown.headers.get("retry-after")) > 0);
  });
});

test("ODsay 자체 한도 초과(오류 응답)면 예비 캐시로 답하고, 오류 응답은 캐시하지 않는다", async () => {
  let fail = false;
  await withServer({ odsay: () => (fail ? odsayQuotaError : odsayOk) }, async ({ get, odsayCalls, advance }) => {
    await get(route(37.61));
    fail = true;
    advance(30 * MIN);
    const saved = await get(route(37.61));
    assert.equal(saved.status, 200);
    assert.equal(((await saved.json()) as { realtime: boolean }).realtime, false);
    assert.equal((await get(route(36.5))).status, 502);
    fail = false;
    const fresh = await get(route(37.61));
    assert.equal(((await fresh.json()) as { realtime: boolean }).realtime, true);
    assert.equal(odsayCalls(), 4);
  });
});

test("경로 캐시 키는 약 50m 단위로 묶어, 조금 흔들린 현재 위치도 같은 캐시를 쓴다", () => {
  const a = routeCacheKey({ originLat: 37.62451, originLng: 127.15301, destinationLat: 37.5826, destinationLng: 127.0105 });
  const b = routeCacheKey({ originLat: 37.62459, originLng: 127.15318, destinationLat: 37.5826, destinationLng: 127.0105 });
  const far = routeCacheKey({ originLat: 37.6260, originLng: 127.1532, destinationLat: 37.5826, destinationLng: 127.0105 });
  assert.equal(a, b);
  assert.notEqual(a, far);
});

test("IP 별 하루 상한: 한 IP 가 몫을 다 쓰면 새 경로는 429, 다른 IP 는 계속 쓸 수 있다", async () => {
  await withServer({ odsayPerIpDailyLimit: 2 }, async ({ get, odsayCalls }) => {
    assert.equal((await get(route(37.61), "1.1.1.1")).status, 200);
    assert.equal((await get(route(37.62), "1.1.1.1")).status, 200);
    // 같은 경로는 캐시라 상한에 들어가지 않는다
    assert.equal((await get(route(37.62), "1.1.1.1")).status, 200);
    assert.equal((await get(route(37.63), "1.1.1.1")).status, 429);
    assert.equal((await get(route(37.63), "2.2.2.2")).status, 200);
    assert.equal(odsayCalls(), 3);
  });
});

test("KeyedDailyQuota 는 키마다 세고 한국 시간 자정에 초기화된다", () => {
  let now = Date.parse("2026-11-26T14:00:00Z"); // 23:00 KST
  const quota = new KeyedDailyQuota(1, () => now);
  assert.equal(quota.take("a"), true);
  assert.equal(quota.take("a"), false);
  assert.equal(quota.take("b"), true);
  now += 2 * 60 * 60 * 1000; // 다음 날 01:00 KST
  assert.equal(quota.take("a"), true);
});
