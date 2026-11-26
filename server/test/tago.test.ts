import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { test } from "node:test";
import { createApp } from "../src/app.ts";
import type { RouteDto } from "../src/contract.ts";
import { firstRideTarget, type OdsayPath, type OdsayResponse } from "../src/odsay.ts";
import { chooseTagoNode, describeUnreadable, parseTagoArrivals, parseTagoNodes } from "../src/realtime.ts";

// 국토교통부 TAGO 응답 형식(_type=json). 부산 서면 근처를 예로 든다.
const stops = {
  response: {
    header: { resultCode: "00", resultMsg: "NORMAL SERVICE." },
    body: {
      items: {
        item: [
          { citycode: 21, gpslati: 35.15745, gpslong: 129.05935, nodeid: "BSB164090301", nodenm: "서면역", nodeno: 9030 },
          { citycode: 21, gpslati: 35.15772, gpslong: 129.05961, nodeid: "BSB164090302", nodenm: "서면역", nodeno: 9031 },
          { citycode: 21, gpslati: 35.15760, gpslong: 129.05900, nodeid: "BSB164090500", nodenm: "서면한전", nodeno: 9050 },
        ],
      },
      numOfRows: 30, pageNo: 1, totalCount: 3,
    },
  },
};

const arrivals = {
  response: {
    header: { resultCode: "00", resultMsg: "NORMAL SERVICE." },
    body: {
      items: {
        item: [
          { arrprevstationcnt: 5, arrtime: 540, nodeid: "BSB164090301", routeid: "BSB5200010000", routeno: 10, routetp: "일반버스" },
          { arrprevstationcnt: 2, arrtime: 180, nodeid: "BSB164090301", routeid: "BSB5200010000", routeno: 10, routetp: "일반버스" },
          { arrprevstationcnt: 1, arrtime: 60, nodeid: "BSB164090301", routeid: "BSB5200860000", routeno: "86", routetp: "일반버스" },
        ],
      },
    },
  },
};

test("TAGO: 정류소 목록을 읽고, 이름이 같은 정류소 중 승차 좌표에 가장 가까운 쪽(길 건너편이 아닌 쪽)을 고른다", () => {
  const nodes = parseTagoNodes(stops)!;
  assert.equal(nodes.length, 3);
  assert.deepEqual(nodes[0], { cityCode: "21", nodeId: "BSB164090301", name: "서면역", lat: 35.15745, lng: 129.05935 });

  // 승차 좌표가 첫 번째 정류소 쪽
  assert.equal(chooseTagoNode(nodes, { lat: 35.15748, lng: 129.05938, stopName: "서면역" })!.nodeId, "BSB164090301");
  // 길 건너편 좌표면 건너편 정류소
  assert.equal(chooseTagoNode(nodes, { lat: 35.15770, lng: 129.05958, stopName: "서면역" })!.nodeId, "BSB164090302");
  // 이름 표기가 조금 달라도(띄어쓰기·정류장) 같은 정류소로 본다
  assert.equal(chooseTagoNode(nodes, { lat: 35.15760, lng: 129.05902, stopName: "서면 한전 정류장" })!.nodeId, "BSB164090500");
});

test("TAGO: 같은 이름이 없으면 가까운 정류소, 120m 안에 없으면 찾지 못한다", () => {
  const nodes = parseTagoNodes(stops)!;
  assert.equal(chooseTagoNode(nodes, { lat: 35.15759, lng: 129.05901, stopName: "다른이름" })!.nodeId, "BSB164090500");
  assert.equal(chooseTagoNode(nodes, { lat: 35.17, lng: 129.07, stopName: "서면역" }), null);
});

test("TAGO: 노선 번호가 같은 버스의 도착 예정 시간(초)을 빠른 순으로 읽는다", () => {
  assert.deepEqual(parseTagoArrivals(arrivals, "10"), [{ seconds: 180 }, { seconds: 540 }]);
  assert.deepEqual(parseTagoArrivals(arrivals, "86번"), [{ seconds: 60 }]);
  assert.deepEqual(parseTagoArrivals(arrivals, "1003"), []);
});

test("TAGO: 결과가 없으면 빈 목록, 결과 코드가 정상이 아니면 null", () => {
  const empty = { response: { header: { resultCode: "00", resultMsg: "NORMAL SERVICE." }, body: { items: "", totalCount: 0 } } };
  assert.deepEqual(parseTagoArrivals(empty, "10"), []);
  assert.deepEqual(parseTagoNodes(empty), []);
  const single = { response: { header: { resultCode: "00" }, body: { items: { item: { arrtime: 300, routeno: "10" } } } } };
  assert.deepEqual(parseTagoArrivals(single, "10"), [{ seconds: 300 }]);

  const notRegistered = { OpenAPI_ServiceResponse: { cmmMsgHeader: { errMsg: "SERVICE_KEY_IS_NOT_REGISTERED_ERROR", returnReasonCode: "30" } } };
  assert.equal(parseTagoArrivals(notRegistered, "10"), null);
  assert.equal(describeUnreadable(notRegistered), "30 SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
});

const walk = { trafficType: 3, sectionTime: 4 };
const busanPath: OdsayPath = {
  info: { totalTime: 30 },
  subPath: [
    walk,
    {
      trafficType: 2, sectionTime: 20, intervalTime: 12, startName: "서면역", startX: 129.05938, startY: 35.15748,
      lane: [{ busNo: "10", busLocalBlID: "5200010000", busCityCode: 7000 }],
      passStopList: { stations: [{ stationName: "서면역", x: "129.05938", y: "35.15748" }, { stationName: "부전시장" }] },
    },
    walk,
  ],
};

test("서울·경기가 아닌 버스는 승차 정류장 좌표와 노선 번호로 TAGO 조회 대상이 된다", () => {
  assert.deepEqual(firstRideTarget(busanPath), {
    kind: "bus",
    query: { region: "tago", lat: 35.15748, lng: 129.05938, stopName: "서면역", routeNo: "10" },
    headwayMinutes: 12,
  });
  // 좌표가 없으면 조회하지 않는다
  const noCoords: OdsayPath = { ...busanPath, subPath: [walk, { trafficType: 2, sectionTime: 20, startName: "서면역", lane: [{ busNo: "10", busCityCode: 7000 }] }] };
  assert.equal(firstRideTarget(noCoords), null);
});

test("부산 버스 경로에 TAGO 실시간 도착을 반영하고, 정류소 찾기는 한 번만 부른다", async () => {
  const odsay: OdsayResponse = { result: { path: [busanPath] } };
  const calls: string[] = [];
  const fakeFetch: typeof fetch = async (input) => {
    const url = String(input);
    calls.push(url);
    if (url.startsWith("https://api.odsay.com/")) return Response.json(odsay);
    if (url.includes("BusSttnInfoInqireService/getCrdntPrxmtSttnList")) return Response.json(stops);
    if (url.includes("ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList")) {
      const params = new URL(url).searchParams;
      assert.equal(params.get("cityCode"), "21");
      assert.equal(params.get("nodeId"), "BSB164090301");
      return Response.json(arrivals);
    }
    throw new Error(`unexpected ${url}`);
  };
  const now = Date.parse("2026-11-25T23:00:00Z"); // 08:00 KST
  let clock = now;
  const server = createApp({
    odsayApiKey: "k", kakaoRestApiKey: "k", realtimeKeys: { dataGoKrKey: "data-key" }, fetchImpl: fakeFetch, clock: () => clock,
  }).listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  const query = "origin_lat=35.16&origin_lng=129.06&destination_lat=35.16&destination_lng=129.06&depart_at=2026-11-25T23:00:00Z";
  try {
    const body = (await (await fetch(`${base}/v1/transit/routes?${query}`)).json()) as { realtime: boolean; routes: RouteDto[] };
    const r = body.routes[0]!;
    // 4분 걸어 정류장 도착, 3분 뒤 10번 → 아직 못 타고, 9분 뒤 차 → 대기 5분
    assert.equal(r.wait_minutes, 5);
    assert.equal(r.segments.find((s) => s.mode === "BUS")!.arrival_message, "9분 후 도착");

    // 30초 캐시가 지난 뒤 다시 조회해도 정류소 찾기는 하루 동안 기억한 값을 쓴다
    clock = now + 60_000;
    await fetch(`${base}/v1/transit/routes?${query.replace("23:00:00Z", "23:01:00Z")}`);
    assert.equal(calls.filter((c) => c.includes("getCrdntPrxmtSttnList")).length, 1);
    assert.equal(calls.filter((c) => c.includes("getSttnAcctoArvlPrearngeInfoList")).length, 2);
  } finally {
    server.close();
  }
});

