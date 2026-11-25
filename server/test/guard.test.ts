import assert from "node:assert/strict";
import { test } from "node:test";
import { clientIp, DailyQuota, RateLimiter, TtlCache } from "../src/guard.ts";

function fakeClock(start: number) {
  let now = start;
  return { now: () => now, advance: (ms: number) => (now += ms) };
}

test("IP 별로 구간 안에서 한도까지만 허용하고, 구간이 지나면 다시 허용한다", () => {
  const clock = fakeClock(0);
  const limiter = new RateLimiter(2, 60_000, clock.now);
  assert.equal(limiter.take("a"), null);
  assert.equal(limiter.take("a"), null);
  assert.equal(limiter.take("a"), 60); // 남은 초
  assert.equal(limiter.take("b"), null); // 다른 IP 는 따로 센다
  clock.advance(59_500);
  assert.equal(limiter.take("a"), 1);
  clock.advance(500);
  assert.equal(limiter.take("a"), null);
});

test("하루 상한은 한국 시간 자정에 초기화된다", () => {
  // 2026-11-25 23:59:00 KST
  const clock = fakeClock(Date.parse("2026-11-25T14:59:00Z"));
  const quota = new DailyQuota(2, clock.now);
  assert.equal(quota.take(), true);
  assert.equal(quota.take(), true);
  assert.equal(quota.take(), false);
  assert.equal(quota.secondsUntilReset(), 60);
  clock.advance(60_000); // 자정
  assert.equal(quota.take(), true);
});

test("캐시는 TTL 이 지나면 비우고, 개수를 넘으면 오래된 것부터 지운다", () => {
  const clock = fakeClock(0);
  const cache = new TtlCache<number>(1_000, 2, clock.now);
  cache.set("a", 1);
  cache.set("b", 2);
  cache.set("c", 3);
  assert.equal(cache.get("a"), undefined);
  assert.equal(cache.get("b"), 2);
  clock.advance(1_000);
  assert.equal(cache.get("c"), undefined);
});

test("실제 IP 는 CF-Connecting-IP 를 쓰고, 없으면 소켓 주소를 쓴다", () => {
  assert.equal(clientIp({ "cf-connecting-ip": "1.2.3.4", "x-forwarded-for": "9.9.9.9" }, "10.0.0.1"), "1.2.3.4");
  assert.equal(clientIp({}, "10.0.0.1"), "10.0.0.1");
});
