import express, { type NextFunction, type Request, type Response } from "express";
import { UpstreamError } from "./contract.ts";
import { fetchKakaoPlaces } from "./kakao.ts";
import { fetchOdsayRoutes } from "./odsay.ts";

export interface AppConfig {
  odsayApiKey: string;
  kakaoRestApiKey: string;
  /** 테스트에서 외부 호출을 대신한다. */
  fetchImpl?: typeof fetch;
}

const MAX_QUERY_LENGTH = 100;

export function createApp(config: AppConfig) {
  const app = express();
  app.disable("x-powered-by");

  app.get("/healthz", (_req, res) => {
    res.json({ ok: true });
  });

  app.get("/v1/transit/routes", async (req, res) => {
    const originLat = latitude(req.query.origin_lat);
    const originLng = longitude(req.query.origin_lng);
    const destinationLat = latitude(req.query.destination_lat);
    const destinationLng = longitude(req.query.destination_lng);
    const departAt = typeof req.query.depart_at === "string" ? new Date(req.query.depart_at) : null;

    if (originLat === null || originLng === null || destinationLat === null || destinationLng === null) {
      res.status(400).json({ error: "origin_lat, origin_lng, destination_lat, destination_lng 가 올바른 좌표여야 합니다." });
      return;
    }
    if (!departAt || Number.isNaN(departAt.getTime())) {
      res.status(400).json({ error: "depart_at 은 ISO-8601 시각이어야 합니다." });
      return;
    }
    const body = await fetchOdsayRoutes(
      config.odsayApiKey,
      { originLat, originLng, destinationLat, destinationLng, departAt },
      config.fetchImpl,
    );
    res.json(body);
  });

  app.get("/v1/places/search", async (req, res) => {
    const query = typeof req.query.query === "string" ? req.query.query.trim() : "";
    if (!query || query.length > MAX_QUERY_LENGTH) {
      res.status(400).json({ error: `query 는 1~${MAX_QUERY_LENGTH}자여야 합니다.` });
      return;
    }
    res.json(await fetchKakaoPlaces(config.kakaoRestApiKey, query, config.fetchImpl));
  });

  app.use((_req, res) => {
    res.status(404).json({ error: "not found" });
  });

  // 외부 API 오류 메시지에는 키가 들어가지 않지만, 앱에는 상태코드만 넘기고 자세한 내용은 서버 로그에만 남긴다.
  app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
    const status = err instanceof UpstreamError ? err.status : 500;
    console.error(`[proxy] ${status}`, err instanceof Error ? err.message : err);
    res.status(status).json({ error: status === 500 ? "internal error" : "upstream error" });
  });

  return app;
}

function latitude(value: unknown): number | null {
  return coordinate(value, 90);
}

function longitude(value: unknown): number | null {
  return coordinate(value, 180);
}

function coordinate(value: unknown, limit: number): number | null {
  if (typeof value !== "string" || value.trim() === "") return null;
  const number = Number(value);
  return Number.isFinite(number) && Math.abs(number) <= limit ? number : null;
}
