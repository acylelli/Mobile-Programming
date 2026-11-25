import assert from "node:assert/strict";
import { test } from "node:test";
import { splitStation, toPlaceResponse } from "../src/kakao.ts";

test("노선별로 나뉜 지하철역은 하나로 합치고 노선을 모은다", () => {
  const { places } = toPlaceResponse({
    documents: [
      { place_name: "잠실역 2호선", address_name: "서울 송파구 신천동 8", road_address_name: "서울 송파구 올림픽로 지하 265", category_group_code: "SW8", x: "127.1002", y: "37.5133" },
      { place_name: "잠실역 8호선", address_name: "서울 송파구 신천동 7-4", road_address_name: "서울 송파구 올림픽로 305", category_group_code: "SW8", x: "127.1032", y: "37.5143" },
      { place_name: "잠실역 공영주차장", address_name: "서울 송파구 신천동 27", road_address_name: "", category_group_code: "PK6", x: "127.1042", y: "37.5144" },
    ],
  });
  assert.deepEqual(places, [
    { name: "잠실역", address: "서울 송파구 올림픽로 지하 265", latitude: 37.5133, longitude: 127.1002, lines: ["2호선", "8호선"] },
    { name: "잠실역 공영주차장", address: "서울 송파구 신천동 27", latitude: 37.5144, longitude: 127.1042, lines: [] },
  ]);
});

test("좌표가 잘못된 항목은 건너뛴다", () => {
  const { places } = toPlaceResponse({ documents: [{ place_name: "이상한 곳", address_name: "", x: "abc", y: "1" }] });
  assert.deepEqual(places, []);
});

test("역 이름과 노선을 나눈다", () => {
  assert.deepEqual(splitStation("한성대입구역 4호선"), { name: "한성대입구역", line: "4호선" });
  assert.deepEqual(splitStation("서울역 경의중앙선"), { name: "서울역", line: "경의중앙선" });
  assert.deepEqual(splitStation("다산역"), { name: "다산역", line: null });
});
