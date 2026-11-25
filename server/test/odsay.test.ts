import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { UpstreamError } from "../src/contract.ts";
import { attachLaneGeometry, lineName, type OdsayResponse, parseLaneGeometry, toRouteResponse } from "../src/odsay.ts";

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

test("버스는 버스 번호에 '번'을 붙여 노선명으로 쓰고 정류장 이름은 그대로 둔다", () => {
  const mixed = toRouteResponse(fixture, departAt).routes[0]!;
  const buses = mixed.segments.filter((s) => s.mode === "BUS");
  assert.deepEqual(
    buses.map((s) => s.line_name),
    ["6번", "종로03번"],
  );
  const bus = buses[0]!;
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

test("이미 '번'으로 끝나는 버스 번호에는 다시 붙이지 않는다", () => {
  assert.equal(lineName({ trafficType: 2, sectionTime: 5, lane: [{ busNo: "N26" }] }), "N26번");
  assert.equal(lineName({ trafficType: 2, sectionTime: 5, lane: [{ busNo: "1번" }] }), "1번");
  assert.equal(lineName({ trafficType: 2, sectionTime: 5, lane: [{ name: "공항버스" }] }), "공항버스");
});

test("탑승 구간에는 지나는 정류장 좌표를 [위도, 경도]로 넣고, 도보 구간에는 넣지 않는다", () => {
  const body: OdsayResponse = {
    result: {
      path: [
        {
          info: { totalTime: 30 },
          subPath: [
            { trafficType: 3, sectionTime: 3 },
            {
              trafficType: 1,
              sectionTime: 25,
              startName: "화랑대",
              endName: "한성대입구",
              lane: [{ name: "수도권 6호선", subwayCode: 6 }],
              passStopList: {
                stations: [
                  { stationName: "화랑대", x: "127.0839781", y: "37.6198821" },
                  { stationName: "봉화산", x: "127.091636", y: "37.617286" },
                  { stationName: "좌표 없음" },
                ],
              },
            },
            { trafficType: 3, sectionTime: 2 },
          ],
        },
      ],
    },
  };
  const segments = toRouteResponse(body, new Date("2026-11-26T08:00:00+09:00")).routes[0]!.segments;
  assert.deepEqual(segments[1]!.points, [
    [37.619882, 127.083978],
    [37.617286, 127.091636],
  ]);
  assert.equal(segments[0]!.points, undefined);
  assert.equal(segments[2]!.points, undefined);
});

// ---- 실제 노선 모양(loadLane) ----

const ridePath = (totalTime: number, mapObj?: string): OdsayResponse["result"] => ({
  path: [
    {
      info: { totalTime, ...(mapObj ? { mapObj } : {}) },
      subPath: [
        { trafficType: 3, sectionTime: 3 },
        {
          trafficType: 2,
          sectionTime: 20,
          startName: "A",
          endName: "B",
          lane: [{ busNo: "76" }],
          passStopList: { stations: [{ stationName: "A", x: "127.1", y: "37.6" }, { stationName: "B", x: "127.2", y: "37.7" }] },
        },
        { trafficType: 1, sectionTime: 10, startName: "C", endName: "D", lane: [{ name: "수도권 6호선" }] },
      ],
    },
  ],
});

test("loadLane 응답을 탑승 구간마다 [위도, 경도] 목록으로 바꾼다", () => {
  const lanes = parseLaneGeometry({
    result: {
      lane: [
        { section: [{ graphPos: [{ x: 127.1, y: 37.6 }, { x: 127.15, y: 37.65 }] }, { graphPos: [{ x: 127.2, y: 37.7 }] }] },
        { section: [{ graphPos: [{ x: 127.3, y: 37.5 }, { x: 127.4, y: 37.4 }] }] },
      ],
    },
  });
  assert.deepEqual(lanes, [
    [[37.6, 127.1], [37.65, 127.15], [37.7, 127.2]],
    [[37.5, 127.3], [37.4, 127.4]],
  ]);
  assert.equal(parseLaneGeometry({ error: { code: "-8" } }), null);
});

test("실제 노선 모양이 있으면 탑승 구간 좌표로 쓰고, 탑승 구간 수와 다르면 정류장 좌표를 쓴다", () => {
  const departAt = new Date("2026-11-26T08:00:00+09:00");
  const body: OdsayResponse = { result: ridePath(40) };
  body.result!.path![0]!.laneGeometry = [
    [[37.6, 127.1], [37.65, 127.12], [37.7, 127.2]],
    [[37.5, 127.3], [37.4, 127.4]],
  ];
  const segments = toRouteResponse(body, departAt).routes[0]!.segments;
  assert.equal(segments[1]!.points!.length, 3);
  assert.deepEqual(segments[2]!.points, [[37.5, 127.3], [37.4, 127.4]]);

  body.result!.path![0]!.laneGeometry = [[[37.6, 127.1], [37.7, 127.2]]];
  const fallback = toRouteResponse(body, departAt).routes[0]!.segments;
  assert.deepEqual(fallback[1]!.points, [[37.6, 127.1], [37.7, 127.2]]);
  assert.equal(fallback[2]!.points, undefined);
});

test("실제 노선 모양은 소요시간이 짧은 상위 경로에만, 하루 상한 안에서만 가져온다", async () => {
  const body: OdsayResponse = {
    result: { path: [...ridePath(50, "slow")!.path!, ...ridePath(30, "fast")!.path!, ...ridePath(40, "mid")!.path!, ...ridePath(35)!.path!] },
  };
  const requested: string[] = [];
  const fakeFetch = (async (url: string) => {
    requested.push(new URL(url).searchParams.get("mapObject")!);
    return new Response(JSON.stringify({ result: { lane: [{ section: [{ graphPos: [{ x: 1, y: 2 }, { x: 3, y: 4 }] }] }, { section: [] }] } }));
  }) as typeof fetch;

  await attachLaneGeometry("key", body, () => true, fakeFetch);
  assert.deepEqual(requested.sort(), ["0:0@fast", "0:0@mid"]);
  assert.ok(body.result!.path!.find((p) => p.info.mapObj === "fast")!.laneGeometry);
  assert.equal(body.result!.path!.find((p) => p.info.mapObj === "slow")!.laneGeometry, undefined);

  requested.length = 0;
  const limited: OdsayResponse = { result: ridePath(30, "fast") };
  await attachLaneGeometry("key", limited, () => false, fakeFetch);
  assert.equal(requested.length, 0);
});

test("실제 노선 모양을 못 가져와도 오류 없이 넘어간다", async () => {
  const body: OdsayResponse = { result: ridePath(30, "fast") };
  const failing = (async () => {
    throw new Error("network");
  }) as typeof fetch;
  await attachLaneGeometry("key", body, () => true, failing);
  assert.equal(body.result!.path![0]!.laneGeometry, undefined);
});

test("같은 노선의 모양은 캐시에 있으면 ODsay 를 다시 부르지 않는다", async () => {
  const store = new Map<string, Array<Array<[number, number]>>>();
  const cache = { get: (k: string) => store.get(k), set: (k: string, v: Array<Array<[number, number]>>) => void store.set(k, v) };
  let calls = 0;
  const fakeFetch = (async () => {
    calls += 1;
    return new Response(JSON.stringify({ result: { lane: [{ section: [{ graphPos: [{ x: 1, y: 2 }, { x: 3, y: 4 }] }] }, { section: [] }] } }));
  }) as typeof fetch;

  await attachLaneGeometry("key", { result: ridePath(30, "fast") }, () => true, fakeFetch, cache);
  const again: OdsayResponse = { result: ridePath(30, "fast") };
  await attachLaneGeometry("key", again, () => true, fakeFetch, cache);

  assert.equal(calls, 1);
  assert.ok(again.result!.path![0]!.laneGeometry);
});
