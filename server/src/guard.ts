/*
 * Proxy 남용 방지.
 *
 * 서버 주소는 앱에 들어 있고 저장소도 공개라서 누구나 호출할 수 있다.
 * 외부 API 무료 한도를 지키기 위해 세 겹으로 막는다.
 *  1. IP 별 분당 요청 수 제한 (RateLimiter)
 *  2. 외부 API 하루 호출 상한 (DailyQuota) — IP 를 바꿔 가며 호출해도 전체 사용량은 넘지 않는다
 *  3. 같은 요청의 짧은 캐시 (TtlCache) — 반복 호출이 외부 API 로 가지 않는다
 *
 * Render 무료 인스턴스는 한 대만 돌기 때문에 상태는 메모리에 둔다. 재시작하면 초기화된다.
 */

export type Clock = () => number;

/** 고정 구간(windowMs) 동안 키마다 limit 번까지 허용한다. */
export class RateLimiter {
  private readonly hits = new Map<string, { count: number; resetAt: number }>();

  constructor(
    private readonly limit: number,
    private readonly windowMs: number,
    private readonly now: Clock = Date.now,
  ) {}

  /** 허용되면 null, 막히면 다시 시도할 수 있을 때까지 남은 초 */
  take(key: string): number | null {
    const now = this.now();
    if (this.hits.size > 10_000) this.sweep(now);
    const entry = this.hits.get(key);
    if (!entry || entry.resetAt <= now) {
      this.hits.set(key, { count: 1, resetAt: now + this.windowMs });
      return null;
    }
    if (entry.count >= this.limit) return Math.max(1, Math.ceil((entry.resetAt - now) / 1000));
    entry.count += 1;
    return null;
  }

  private sweep(now: number) {
    for (const [key, entry] of this.hits) if (entry.resetAt <= now) this.hits.delete(key);
  }
}

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

/** 한국 시간 자정마다 초기화되는 하루 호출 상한 */
export class DailyQuota {
  private used = 0;
  private day = -1;

  constructor(
    readonly limit: number,
    private readonly now: Clock = Date.now,
  ) {}

  /** 상한 안이면 1 을 쓰고 true. 넘으면 false */
  take(): boolean {
    const today = Math.floor((this.now() + KST_OFFSET_MS) / DAY_MS);
    if (today !== this.day) {
      this.day = today;
      this.used = 0;
    }
    if (this.used >= this.limit) return false;
    this.used += 1;
    return true;
  }

  /** 다음 한국 시간 자정까지 남은 초 */
  secondsUntilReset(): number {
    const now = this.now() + KST_OFFSET_MS;
    return Math.ceil((DAY_MS - (now % DAY_MS)) / 1000);
  }
}

/** 오래된 것부터 지우는 크기 제한 캐시 */
export class TtlCache<T> {
  private readonly entries = new Map<string, { value: T; expiresAt: number }>();

  constructor(
    private readonly ttlMs: number,
    private readonly maxEntries: number,
    private readonly now: Clock = Date.now,
  ) {}

  get(key: string): T | undefined {
    const entry = this.entries.get(key);
    if (!entry) return undefined;
    if (entry.expiresAt <= this.now()) {
      this.entries.delete(key);
      return undefined;
    }
    return entry.value;
  }

  set(key: string, value: T) {
    this.entries.delete(key);
    this.entries.set(key, { value, expiresAt: this.now() + this.ttlMs });
    while (this.entries.size > this.maxEntries) {
      const oldest = this.entries.keys().next().value;
      if (oldest === undefined) break;
      this.entries.delete(oldest);
    }
  }
}

/**
 * 요청한 사람의 IP.
 * Render 앞단(Cloudflare)이 CF-Connecting-IP 에 실제 IP 를 넣고, 사용자가 보낸 같은 이름의 헤더는 덮어쓴다.
 * 그 헤더가 없을 때(로컬 실행 등)는 소켓 주소를 쓴다.
 */
export function clientIp(headers: Record<string, string | string[] | undefined>, socketAddress: string | undefined): string {
  const cf = headers["cf-connecting-ip"];
  const value = Array.isArray(cf) ? cf[0] : cf;
  return value?.trim() || socketAddress || "unknown";
}
