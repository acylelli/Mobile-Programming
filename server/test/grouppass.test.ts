import assert from "node:assert/strict";
import type { AddressInfo } from "node:net";
import { test } from "node:test";
import { createApp } from "../src/app.ts";
import { parseGroupPasses } from "../src/grouppass.ts";

const now = Date.parse("2026-11-25T10:00:00Z");

async function withServer(run: (base: string) => Promise<void>) {
  const server = createApp({ odsayApiKey: "k", kakaoRestApiKey: "k", clock: () => now }).listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  try {
    await run(`http://127.0.0.1:${(server.address() as AddressInfo).port}`);
  } finally {
    server.close();
  }
}

test("코드를 확인하면 기관·기간·남은 인원을 앱 계약 형식으로 준다(대소문자 무시)", async () => {
  await withServer(async (base) => {
    const res = await fetch(`${base}/v1/group-passes/hsu-2026-wake7`);
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), {
      code: "HSU-2026-WAKE7",
      organization: "한성대학교",
      valid_from: "2026-03-01",
      valid_until: "2027-02-28",
      remaining_seats: 128,
    });
    assert.equal((await fetch(`${base}/v1/group-passes/NOPE-0000-CODE`)).status, 404);
  });
});

test("사용하면 남은 인원이 줄고, 만료·정원 초과 코드는 사용되지 않는다", async () => {
  await withServer(async (base) => {
    const redeemed = await fetch(`${base}/v1/group-passes/HSU-2026-WAKE7/redeem`, { method: "POST" });
    assert.equal(((await redeemed.json()) as { remaining_seats: number }).remaining_seats, 127);
    const after = await fetch(`${base}/v1/group-passes/HSU-2026-WAKE7`);
    assert.equal(((await after.json()) as { remaining_seats: number }).remaining_seats, 127);

    assert.equal((await fetch(`${base}/v1/group-passes/HSU-2025-PAST1/redeem`, { method: "POST" })).status, 409);
    assert.equal((await fetch(`${base}/v1/group-passes/HSU-2026-FULL0/redeem`, { method: "POST" })).status, 409);
    assert.equal((await fetch(`${base}/v1/group-passes/NOPE-0000-CODE/redeem`, { method: "POST" })).status, 404);
  });
});

test("GROUP_PASSES 환경변수로 코드를 바꿀 수 있고, 형식이 틀리면 거절한다", () => {
  const passes = parseGroupPasses(
    JSON.stringify([{ code: "abc-2027-team1", organization: "A", valid_from: "2027-01-01", valid_until: "2027-12-31", remaining_seats: 3 }]),
  );
  assert.equal(passes[0]!.code, "ABC-2027-TEAM1");
  assert.equal(parseGroupPasses(undefined).length, 4);
  assert.throws(() => parseGroupPasses("{}"));
  assert.throws(() => parseGroupPasses(JSON.stringify([{ code: "X" }])));
});
