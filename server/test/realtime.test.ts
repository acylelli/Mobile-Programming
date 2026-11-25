import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { test } from "node:test";
import { createApp } from "../src/app.ts";
import type { RouteDto } from "../src/contract.ts";
import { firstRideTarget, type OdsayPath, type OdsayResponse } from "../src/odsay.ts";
import { applyArrival, firstCatchable, parseGyeonggiBusArrivals, parseSeoulBusArrivals, parseSubwayArrivals } from "../src/realtime.ts";

// ---- 응답 파싱 (각 API 문서의 응답 형식) ----

test("지하철: 노선·방향(다음 역)이 맞는 열차만, 남은 시간을 초로 읽는다", () => {
  const body = {
    realtimeArrivalList: [
      { subwayId: "1008", trainLineNm: "별내행 - 별내방면", barvlDt: "0", arvlMsg2: "3분 20초 후 (구리)" },
      { subwayId: "1008", trainLineNm: "모란행 - 동구릉방면", barvlDt: "60", arvlMsg2: "1분 후" },
      { subwayId: "1008", trainLineNm: "별내행 - 별내방면", barvlDt: "0", arvlMsg2: "[4]번째 전역 (암사)" },
      { subwayId: "1008", trainLineNm: "별내행 - 별내방면", barvlDt: "0", arvlMsg2: "다산 도착" },
      { subwayId: "1067", trainLineNm: "춘천행 - 별내방면", barvlDt: "100", arvlMsg2: "1분 40초 후" },
    ],
  };
  const arrivals = parseSubwayArrivals(body, { station: "다산", nextStation: "별내", odsayLineCode: 8 });
  assert.deepEqual(arrivals, [{ seconds: 0 }, { seconds: 200 }, { seconds: 480 }]);
});

test("지하철: 목록이 없으면(키 오류 등) null", () => {
  assert.equal(parseSubwayArrivals({ status: 500, code: "INFO-100" }, { station: "다산", nextStation: "별내" }), null);
});

test("서울 버스: 노선 ID 가 같은 항목의 traTime1·2(초)", () => {
  const body = {
    msgBody: {
      itemList: [
        { busRouteId: "100100021", rtNm: "142", traTime1: "543", traTime2: "977", arrmsg1: "9분3초후[3번째 전]" },
        { busRouteId: "107900003", rtNm: "성북02", traTime1: "0", arrmsg1: "곧 도착", traTime2: "620", arrmsg2: "10분20초후[4번째 전]" },
      ],
    },
  };
  assert.deepEqual(parseSeoulBusArrivals(body, "107900003"), [{ seconds: 30 }, { seconds: 620 }]);
  assert.deepEqual(parseSeoulBusArrivals(body, "999"), []);
  // 항목이 하나면 객체로 온다
  assert.deepEqual(parseSeoulBusArrivals({ msgBody: { itemList: body.msgBody.itemList[0] } }, "100100021"), [{ seconds: 543 }, { seconds: 977 }]);
});

test("경기 버스: 초 단위 값이 있으면 쓰고, 없으면 분 단위 값을 쓴다", () => {
  const body = {
    response: {
      msgBody: {
        busArrivalList: [
          { routeId: 222000181, routeName: "76", predictTime1: 5, predictTime2: 17 },
          { routeId: 222000195, routeName: "땡큐90", predictTimeSec1: 250, predictTime1: 4, predictTime2: "" },
        ],
      },
    },
  };
  assert.deepEqual(parseGyeonggiBusArrivals(body, "222000181"), [{ seconds: 300 }, { seconds: 1020 }]);
  assert.deepEqual(parseGyeonggiBusArrivals(body, "222000195"), [{ seconds: 250 }]);
  assert.equal(parseGyeonggiBusArrivals({ response: {} }, "1"), null);
});

// ---- ODsay 경로에서 조회 대상 고르기 ----

const walk = { trafficType: 3, sectionTime: 4 };

test("첫 탑승 구간에 맞는 조회 대상을 고른다", () => {
  const subway: OdsayPath = {
    info: { totalTime: 30 },
    subPath: [walk, { trafficType: 1, sectionTime: 20, startName: "다산", lane: [{ name: "수도권 8호선", subwayCode: 8 }], passStopList: { stations: [{ stationName: "다산" }, { stationName: "별내" }] } }],
  };
  assert.deepEqual(firstRideTarget(subway), { kind: "subway", query: { station: "다산", nextStation: "별내", odsayLineCode: 8 } });

  const seoulBus: OdsayPath = {
    info: { totalTime: 30 },
    subPath: [walk, { trafficType: 2, sectionTime: 10, startArsID: "08-718", lane: [{ busNo: "성북02", busLocalBlID: "107900003", busCityCode: 1000 }] }],
  };
  assert.deepEqual(firstRideTarget(seoulBus), { kind: "bus", query: { region: "seoul", stationKey: "08718", routeId: "107900003" } });

  const gyeonggiBus: OdsayPath = {
    info: { totalTime: 30 },
    subPath: [walk, { trafficType: 2, sectionTime: 10, startLocalStationID: "222000172", lane: [{ busNo: "76", busLocalBlID: "222000181", busCityCode: 1120 }] }],
  };
  assert.deepEqual(firstRideTarget(gyeonggiBus), { kind: "bus", query: { region: "gyeonggi", stationKey: "222000172", routeId: "222000181" } });

  const incheonBus: OdsayPath = {
    info: { totalTime: 30 },
    subPath: [walk, { trafficType: 2, sectionTime: 10, startLocalStationID: "168000123", lane: [{ busNo: "9100", busLocalBlID: "165000001", busCityCode: 2000 }] }],
  };
  assert.equal(firstRideTarget(incheonBus), null);
});

// ---- 경로에 반영 ----

const route: RouteDto = {
  total_minutes: 40,
  walk_minutes: 4,
  wait_minutes: 2,
  transfer_minutes: 0,
  transfer_count: 0,
  delay_minutes: 0,
  departure_time: "2026-11-26T08:00:00+09:00",
  arrival_time: "2026-11-26T08:40:00+09:00",
  segments: [
    { mode: "WALK", from: "출발지", to: "다산역", minutes: 4 },
    { mode: "SUBWAY", line_name: "8호선", from: "다산역", to: "잠실역", minutes: 34 },
  ],
};

test("정류장에 닿은 뒤 처음 오는 차로 대기 시간·도착 안내·도착 시각을 다시 계산한다", () => {
  // 지금 출발 → 4분 뒤 역 도착. 3분 뒤 열차는 놓치고 9분 뒤 열차를 탄다.
  const catchable = firstCatchable([{ seconds: 180 }, { seconds: 540 }], 240)!;
  assert.deepEqual(catchable, { seconds: 540 });
  const adjusted = applyArrival(route, 240, catchable);
  assert.equal(adjusted.wait_minutes, 5);
  assert.equal(adjusted.total_minutes, 43);
  assert.equal(adjusted.arrival_time, "2026-11-26T08:43:00+09:00");
  assert.equal(adjusted.segments[1]!.arrival_message, "9분 후 도착");
  assert.equal(adjusted.segments[0]!.arrival_message, undefined);
});

test("탈 수 있는 차가 없으면 보정하지 않는다", () => {
  assert.equal(firstCatchable([{ seconds: 60 }], 240), null);
});

// ---- 서버 전체 흐름 ----

const odsayBody: OdsayResponse = {
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

async function withServer(now: number, keys: { seoulSubwayKey?: string }, run: (base: string, calls: string[]) => Promise<void>) {
  const calls: string[] = [];
  const fakeFetch: typeof fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    if (url.startsWith("https://api.odsay.com/")) return Response.json(odsayBody);
    if (url.includes("swopenapi.seoul.go.kr")) {
      return Response.json({ realtimeArrivalList: [{ subwayId: "1008", trainLineNm: "별내행 - 별내방면", barvlDt: "420", arvlMsg2: "7분 후" }] });
    }
    throw new Error(`unexpected ${url}`);
  };
  const server = createApp({ odsayApiKey: "k", kakaoRestApiKey: "k", realtimeKeys: keys, fetchImpl: fakeFetch, clock: () => now }).listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  try {
    await run(`http://127.0.0.1:${(server.address() as AddressInfo).port}`, calls);
  } finally {
    server.close();
  }
}

const now = Date.parse("2026-11-25T23:00:00Z"); // 08:00 KST
const query = "origin_lat=37.62&origin_lng=127.15&destination_lat=37.51&destination_lng=127.10";
const realtimeCalls = (calls: string[]) => calls.filter((c) => c.includes("swopenapi")).length;

test("출발이 30분 이내면 실시간 도착을 반영한다", async () => {
  await withServer(now, { seoulSubwayKey: "subway-key" }, async (base, calls) => {
    const res = await fetch(`${base}/v1/transit/routes?${query}&depart_at=2026-11-25T23:00:00Z`);
    const body = (await res.json()) as { routes: RouteDto[] };
    const r = body.routes[0]!;
    // 4분 걸어 역 도착, 7분 뒤 열차 → 대기 3분, 총 40 - 0 + 3 = 43분
    assert.equal(r.wait_minutes, 3);
    assert.equal(r.total_minutes, 43);
    assert.equal(r.segments[1]!.arrival_message, "7분 후 도착");
    assert.equal(realtimeCalls(calls), 1);
  });
});

test("출발이 30분보다 멀거나 키가 없으면 실시간 API 를 부르지 않는다", async () => {
  await withServer(now, { seoulSubwayKey: "subway-key" }, async (base, calls) => {
    const res = await fetch(`${base}/v1/transit/routes?${query}&depart_at=2026-11-26T22:00:00Z`);
    const r = ((await res.json()) as { routes: RouteDto[] }).routes[0]!;
    assert.equal(r.segments[1]!.arrival_message, undefined);
    assert.equal(realtimeCalls(calls), 0);
  });
  await withServer(now, {}, async (base, calls) => {
    await fetch(`${base}/v1/transit/routes?${query}&depart_at=2026-11-25T23:00:00Z`);
    assert.equal(realtimeCalls(calls), 0);
  });
});
