import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { after, before, test } from "node:test";
import { createApp } from "../src/app.ts";

const calls: string[] = [];
const fakeFetch: typeof fetch = async (input) => {
  const url = String(input);
  calls.push(url);
  if (url.startsWith("https://apis-navi.kakaomobility.com/")) {
    return Response.json({ routes: [{ result_code: 0, summary: { duration: 1401, distance: 16757, fare: { taxi: 24000, toll: 0 } } }] });
  }
  if (url.startsWith("https://api.odsay.com/")) {
    return Response.json({ error: [{ code: "500", message: "[ApiKeyAuthFailed] ApiKey authentication failed." }] });
  }
  return Response.json({ documents: [{ place_name: "한성대학교", address_name: "서울 성북구 삼선동2가 389", road_address_name: "서울 성북구 삼선교로16길 116", x: "127.0105", y: "37.5826" }] });
};

let server: ReturnType<ReturnType<typeof createApp>["listen"]>;
let base = "";

before(async () => {
  server = createApp({ odsayApiKey: "test-odsay", kakaoRestApiKey: "test-kakao", fetchImpl: fakeFetch }).listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});

after(() => {
  server.close();
});

test("좌표가 없거나 범위를 벗어나면 외부 API 를 부르지 않고 400", async () => {
  const before = calls.length;
  const missing = await fetch(`${base}/v1/transit/routes?origin_lat=37.5&origin_lng=127&destination_lat=37.6`);
  const outOfRange = await fetch(`${base}/v1/transit/routes?origin_lat=99&origin_lng=127&destination_lat=37.6&destination_lng=127&depart_at=2026-10-05T22:45:00Z`);
  const badTime = await fetch(`${base}/v1/transit/routes?origin_lat=37.5&origin_lng=127&destination_lat=37.6&destination_lng=127&depart_at=tomorrow`);
  assert.deepEqual([missing.status, outOfRange.status, badTime.status], [400, 400, 400]);
  assert.equal(calls.length, before);
});

test("외부 API 오류는 502 로 돌려주고 응답에 원문 메시지를 싣지 않는다", async () => {
  const res = await fetch(`${base}/v1/transit/routes?origin_lat=37.61&origin_lng=127.14&destination_lat=37.58&destination_lng=127.01&depart_at=2026-10-05T22:45:00Z`);
  assert.equal(res.status, 502);
  assert.deepEqual(await res.json(), { error: "upstream error" });
});

test("장소 검색은 계약 형식으로 응답하고 빈 검색어는 400", async () => {
  const ok = await fetch(`${base}/v1/places/search?query=${encodeURIComponent("한성대")}`);
  assert.equal(ok.status, 200);
  assert.deepEqual(await ok.json(), {
    places: [{ name: "한성대학교", address: "서울 성북구 삼선교로16길 116", latitude: 37.5826, longitude: 127.0105, lines: [] }],
  });
  assert.equal((await fetch(`${base}/v1/places/search?query=%20`)).status, 400);
});

test("IP 별 분당 한도를 넘으면 429 와 Retry-After 를 준다", async () => {
  const limited = createApp({
    odsayApiKey: "k",
    kakaoRestApiKey: "k",
    fetchImpl: fakeFetch,
    limits: { searchPerMinute: 2 },
  }).listen(0);
  await new Promise((resolve) => limited.once("listening", resolve));
  const url = `http://127.0.0.1:${(limited.address() as AddressInfo).port}/v1/places/search?query=a`;
  try {
    const statuses = [];
    for (let i = 0; i < 3; i++) statuses.push((await fetch(url, { headers: { "CF-Connecting-IP": "1.1.1.1" } })).status);
    assert.deepEqual(statuses, [200, 200, 429]);
    const blocked = await fetch(url, { headers: { "CF-Connecting-IP": "1.1.1.1" } });
    assert.ok(Number(blocked.headers.get("retry-after")) > 0);
    // 다른 사람은 영향을 받지 않는다
    assert.equal((await fetch(url, { headers: { "CF-Connecting-IP": "2.2.2.2" } })).status, 200);
  } finally {
    limited.close();
  }
});

test("같은 검색어는 캐시로 응답하고, 하루 상한을 넘으면 외부 API 를 부르지 않고 429", async () => {
  const counted: string[] = [];
  const countingFetch: typeof fetch = async (input, init) => {
    counted.push(String(input));
    return fakeFetch(input, init);
  };
  const quotaApp = createApp({
    odsayApiKey: "k",
    kakaoRestApiKey: "k",
    fetchImpl: countingFetch,
    limits: { kakaoDailyLimit: 1 },
  }).listen(0);
  await new Promise((resolve) => quotaApp.once("listening", resolve));
  const base = `http://127.0.0.1:${(quotaApp.address() as AddressInfo).port}/v1/places/search?query=`;
  try {
    assert.equal((await fetch(`${base}hansung`)).status, 200);
    assert.equal((await fetch(`${base}%20HANSUNG%20`)).status, 200); // 대소문자·공백만 다르면 같은 검색어
    assert.equal(counted.length, 1);
    const over = await fetch(`${base}jamsil`);
    assert.equal(over.status, 429);
    assert.equal(counted.length, 1);
  } finally {
    quotaApp.close();
  }
});

test("택시 예상 시간·요금은 분 단위로 올려 계약 형식으로 주고, 같은 출발·도착은 캐시한다", async () => {
  const query = "origin_lat=37.6243&origin_lng=127.1544&destination_lat=37.5826&destination_lng=127.0105";
  const before = calls.filter((u) => u.includes("kakaomobility")).length;
  const first = await fetch(`${base}/v1/taxi/estimate?${query}`);
  assert.equal(first.status, 200);
  assert.deepEqual(await first.json(), { minutes: 24, distance_meters: 16757, fare_won: 24000, toll_won: 0 });
  await fetch(`${base}/v1/taxi/estimate?${query}`);
  assert.equal(calls.filter((u) => u.includes("kakaomobility")).length, before + 1);
  // 키는 헤더로만 보내고 주소에는 싣지 않는다
  assert.ok(!calls.some((u) => u.includes("kakaomobility") && u.includes("test-kakao")));
});

test("택시 예상은 좌표가 잘못되면 400", async () => {
  const res = await fetch(`${base}/v1/taxi/estimate?origin_lat=999&origin_lng=127&destination_lat=37&destination_lng=127`);
  assert.equal(res.status, 400);
});
