import assert from "node:assert/strict";
import { test } from "node:test";
import { toTaxiEstimate } from "../src/taxi.ts";

test("카카오모빌리티 응답을 분(올림)·미터·원으로 바꾼다", () => {
  assert.deepEqual(toTaxiEstimate({ routes: [{ result_code: 0, summary: { duration: 61, distance: 512.4, fare: { taxi: 4800, toll: 900 } } }] }), {
    minutes: 2,
    distance_meters: 512,
    fare_won: 4800,
    toll_won: 900,
  });
});

test("길을 찾지 못한 응답은 null", () => {
  assert.equal(toTaxiEstimate({ routes: [{ result_code: 104 }] }), null);
  assert.equal(toTaxiEstimate({}), null);
});
