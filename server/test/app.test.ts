import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { after, before, test } from "node:test";
import { createApp } from "../src/app.ts";

const calls: string[] = [];
const fakeFetch: typeof fetch = async (input) => {
  const url = String(input);
  calls.push(url);
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
