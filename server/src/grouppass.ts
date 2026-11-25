import express from "express";
import { type Clock, clientIp, RateLimiter } from "./guard.ts";

/*
 * 단체 이용권(학교·기업이 인원만큼 구매해 구성원에게 PRO 를 주는 코드) 확인·사용.
 *
 * - GET  /v1/group-passes/{code}        : 기관·이용 기간·남은 인원 확인(사용하지 않음)
 * - POST /v1/group-passes/{code}/redeem : 사용 처리(남은 인원 1 감소)
 *
 * 응답은 앱 계약(GroupPassDto)과 같다. 만료·정원 초과 판단은 앱이 응답을 보고 안내한다.
 * 코드 목록은 환경변수 GROUP_PASSES(JSON 배열)로 바꿀 수 있고, 없으면 아래 테스트 코드를 쓴다.
 * 남은 인원은 메모리에서만 줄어들어 서버가 재시작하면 처음 값으로 돌아간다(테스트 범위).
 * 기관에는 이용 인원만 집계해 제공하므로, 누가 사용했는지(위치·일정)는 받지도 저장하지도 않는다.
 */

export interface GroupPassConfig {
  code: string;
  organization: string;
  /** ISO-8601 날짜 */
  valid_from: string;
  valid_until: string;
  remaining_seats: number;
}

/** 테스트 코드. 첫 번째는 UI 설계서(UI-E-002) 예시와 같고, 나머지는 만료·정원 초과 확인용이다. */
export const DEFAULT_GROUP_PASSES: GroupPassConfig[] = [
  { code: "HSU-2026-WAKE7", organization: "한성대학교", valid_from: "2026-03-01", valid_until: "2027-02-28", remaining_seats: 128 },
  { code: "WAKE-2026-TEAM1", organization: "WAKE 물류센터", valid_from: "2026-01-01", valid_until: "2026-12-31", remaining_seats: 12 },
  { code: "HSU-2025-PAST1", organization: "한성대학교", valid_from: "2025-03-01", valid_until: "2026-02-28", remaining_seats: 40 },
  { code: "HSU-2026-FULL0", organization: "한성대학교", valid_from: "2026-03-01", valid_until: "2027-02-28", remaining_seats: 0 },
];

/** GROUP_PASSES 환경변수를 읽는다. 형식이 틀리면 서버를 띄우지 않도록 예외를 던진다. */
export function parseGroupPasses(raw: string | undefined): GroupPassConfig[] {
  if (!raw?.trim()) return DEFAULT_GROUP_PASSES;
  const parsed = JSON.parse(raw) as unknown;
  if (!Array.isArray(parsed)) throw new Error("GROUP_PASSES 는 JSON 배열이어야 합니다.");
  return parsed.map((item, i) => {
    const p = item as Partial<GroupPassConfig>;
    const valid =
      typeof p.code === "string" &&
      typeof p.organization === "string" &&
      /^\d{4}-\d{2}-\d{2}$/.test(String(p.valid_from)) &&
      /^\d{4}-\d{2}-\d{2}$/.test(String(p.valid_until)) &&
      Number.isInteger(p.remaining_seats) &&
      (p.remaining_seats as number) >= 0;
    if (!valid) throw new Error(`GROUP_PASSES[${i}] 형식이 올바르지 않습니다.`);
    return { ...(p as GroupPassConfig), code: p.code!.trim().toUpperCase() };
  });
}

export function createGroupPassRouter(passes: GroupPassConfig[], clock: Clock) {
  const router = express.Router();
  // 코드를 하나씩 대입해 보는 시도를 막는다. 사람이 입력하는 속도로는 넉넉하다.
  const limiter = new RateLimiter(20, 60_000, clock);
  const byCode = new Map(passes.map((p) => [p.code, { ...p }]));

  router.use("/v1/group-passes", (req, res, next) => {
    const retryAfter = limiter.take(clientIp(req.headers, req.socket.remoteAddress));
    if (retryAfter !== null) {
      res.set("Retry-After", String(retryAfter)).status(429).json({ error: "too many requests" });
      return;
    }
    next();
  });

  router.get("/v1/group-passes/:code", (req, res) => {
    const pass = byCode.get(normalize(req.params.code));
    if (!pass) {
      res.status(404).json({ error: "unknown code" });
      return;
    }
    res.json(pass);
  });

  router.post("/v1/group-passes/:code/redeem", (req, res) => {
    const pass = byCode.get(normalize(req.params.code));
    if (!pass) {
      res.status(404).json({ error: "unknown code" });
      return;
    }
    const today = new Date(clock() + 9 * 3_600_000).toISOString().slice(0, 10); // 한국 날짜
    if (pass.valid_until < today || pass.valid_from > today) {
      res.status(409).json({ error: "expired", ...pass });
      return;
    }
    if (pass.remaining_seats <= 0) {
      res.status(409).json({ error: "full", ...pass });
      return;
    }
    pass.remaining_seats -= 1;
    res.json(pass);
  });

  return router;
}

function normalize(code: string): string {
  return code.trim().toUpperCase();
}
