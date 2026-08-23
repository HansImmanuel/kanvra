import { describe, it, expect, vi, beforeEach } from "vitest";

import { api, ApiError } from "./api";
import type { ApiErrorBody } from "@/types";

/** Minimal JSON Response helper. */
function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

describe("api client", () => {
  let fetchStub: ReturnType<typeof vi.fn<typeof fetch>>;

  beforeEach(() => {
    fetchStub = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchStub);
  });

  it("throws an ApiError carrying the parsed body on a non-OK response", async () => {
    const body: ApiErrorBody = { status: 409, code: "TASK_VERSION_CONFLICT", message: "conflict" };
    fetchStub.mockResolvedValue(jsonResponse(body, 409));

    const err = await api("/api/v1/tasks/5/move", { method: "POST", body: {} }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(409);
    expect((err as ApiError).code).toBe("TASK_VERSION_CONFLICT");
    expect((err as ApiError).data).toMatchObject({ status: 409, code: "TASK_VERSION_CONFLICT" });
  });

  it("generates an Idempotency-Key once per mutation and reuses it on a 401 refresh-retry", async () => {
    // First attempt 401, refresh succeeds, retry succeeds.
    fetchStub
      .mockResolvedValueOnce(jsonResponse({ code: "UNAUTHORIZED" }, 401))
      .mockResolvedValueOnce(jsonResponse({ user: {} }, 200)) // /auth/refresh
      .mockResolvedValueOnce(jsonResponse({ id: 1, eventId: "evt-1" }, 201));

    const result = await api<{ id: number }>("/api/v1/columns/1/tasks", {
      method: "POST",
      body: { title: "A" }
    });

    expect(result.id).toBe(1);
    // 3 fetches: original, refresh, retry.
    expect(fetchStub).toHaveBeenCalledTimes(3);

    const calls = fetchStub.mock.calls as [string, RequestInit][];
    // The original mutation + the retry must carry the SAME Idempotency-Key.
    const key1 = (calls[0][1].headers as Record<string, string>)["Idempotency-Key"];
    const key3 = (calls[2][1].headers as Record<string, string>)["Idempotency-Key"];
    expect(key1).toBeTruthy();
    expect(key3).toBe(key1);
  });

  it("does not attach an Idempotency-Key to GET requests", async () => {
    fetchStub.mockResolvedValue(jsonResponse({}, 200));
    await api("/api/v1/boards/1");
    const headers = (fetchStub.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect("Idempotency-Key" in (headers ?? {})).toBe(false);
  });
});