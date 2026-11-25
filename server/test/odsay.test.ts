import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { UpstreamError } from "../src/contract.ts";
import { type OdsayResponse, toRouteResponse } from "../src/odsay.ts";

// 동구릉 근처 → 한성대학교 실제 ODsay 응답에서 필요한 필드만 남긴 것
const fixture = JSON.parse(readFileSync(new URL("./fixtures/odsay-routes.json", import.meta.url), "utf8")) as OdsayResponse;
const departAt = new Date("2026-10-05T22:45:00Z");

test("소요시간이 짧은 경로부터 정렬하고 출발·도착 시각을 KST 로 준다", () => {
  const { realtime, routes } = toRouteResponse(fixture, departAt);
  assert.equal(realtime, true);
  assert.deepEqual(
    routes.map((r) => r.total_minutes),
    [60, 64],
  );
  assert.equal(routes[1]!.departure_time, "2026-10-06T07:45:00+09:00");
  assert.equal(routes[1]!.arrival_time, "2026-10-06T08:49:00+09:00");
});

test("처음·마지막 도보는 WALK 구간, 탈것 사이 도보는 환승 시간으로 합친다", () => {
  const subway = toRouteResponse(fixture, departAt).routes[1]!;
  assert.deepEqual(subway.segments, [
    { mode: "WALK", from: "출발지", to: "동구릉역", minutes: 7 },
    { mode: "SUBWAY", line_name: "8호선", from: "동구릉역", to: "천호역", minutes: 12 },
    { mode: "SUBWAY", line_name: "5호선", from: "천호역", to: "동대문역사문화공원역", minutes: 22 },
    { mode: "SUBWAY", line_name: "4호선", from: "동대문역사문화공원역", to: "혜화역", minutes: 4 },
    { mode: "WALK", from: "혜화역", to: "도착지", minutes: 11 },
  ]);
  assert.equal(subway.walk_minutes, 18);
  assert.equal(subway.transfer_minutes, 4);
  assert.equal(subway.transfer_count, 2);
  // ODsay 총 64분 - 구간 합 60분 = 대기 4분. 구간 합 + 환승 + 대기 = 총 소요시간
  assert.equal(subway.wait_minutes, 4);
  const sum = subway.segments.reduce((acc, s) => acc + s.minutes, 0) + subway.transfer_minutes + subway.wait_minutes;
  assert.equal(sum, subway.total_minutes);
});

test("버스는 버스 번호를 노선명으로 쓰고 정류장 이름은 그대로 둔다", () => {
  const mixed = toRouteResponse(fixture, departAt).routes[0]!;
  const bus = mixed.segments.find((s) => s.mode === "BUS")!;
  assert.equal(bus.line_name, "6");
  assert.equal(bus.from, "농수산물시장");
  assert.ok(mixed.segments.some((s) => s.line_name === "경의중앙선"));
});

test("출발·도착이 가깝거나 정류장이 없으면 빈 경로로 응답한다", () => {
  assert.deepEqual(toRouteResponse({ error: { code: "-98", msg: "출, 도착지가 700m이내입니다." } }, departAt).routes, []);
  assert.deepEqual(toRouteResponse({ error: { code: "3", msg: "출발지 정류장이 없습니다." } }, departAt).routes, []);
});

test("키 인증 실패 같은 오류는 502 로 던진다", () => {
  assert.throws(
    () => toRouteResponse({ error: [{ code: "500", message: "[ApiKeyAuthFailed] ApiKey authentication failed." }] }, departAt),
    (e: unknown) => e instanceof UpstreamError && e.status === 502,
  );
});

test("도보만 있는 경로는 버린다", () => {
  const walkOnly: OdsayResponse = { result: { path: [{ info: { totalTime: 10 }, subPath: [{ trafficType: 3, sectionTime: 10 }] }] } };
  assert.deepEqual(toRouteResponse(walkOnly, departAt).routes, []);
});
