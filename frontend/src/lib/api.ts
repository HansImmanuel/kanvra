// API client (docs/TECH_DOC.md §14 lib/api).
//
// - Calls are same-origin; Next rewrites /api/* to the Spring Boot backend
//   (:8080) and a middleware injects the CSRF double-submit header from the
//   csrf_token cookie. (Cookies are httpOnly/JS-invisible, so the proxy — not
//   browser JS — is where the double-submit value is sourced.)
// - Idempotency-Key is generated once per logical mutation and REUSED on retry so
//   a failed-then-retried create cannot duplicate (docs/ADR-008).
// - On 401 the client silently refreshes once (POST /auth/refresh) and retries
//   the original request exactly once — the local-session flow.

import type { ApiErrorBody } from "../types";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly data: ApiErrorBody | null;
  constructor(status: number, code: string, message: string, data: ApiErrorBody | null = null) {
    super(message || `Request failed with HTTP ${status}`);
    this.status = status;
    this.code = code || "UNKNOWN";
    this.data = data;
  }
}

const MUTATING = new Set(["POST", "PUT", "PATCH", "DELETE"]);

interface ApiOptions {
  method?: string;
  body?: unknown;
  /** Send an Idempotency-Key (defaults to true for mutating calls). */
  idempotent?: boolean;
}

const createIdempotencyKey = (): string =>
  typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;

export async function api<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const method = (options.method ?? "GET").toUpperCase();
  const isMutating = MUTATING.has(method);
  const idempotencyKey: string | null =
    isMutating && (options.idempotent ?? true) ? createIdempotencyKey() : null;

  const oneAttempt = async (): Promise<Response> => {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (isMutating) {
      headers["Content-Type"] = "application/json";
      if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;
      // CSRF header is injected by the middleware from the csrf_token cookie.
    }
    return fetch(path, {
      method,
      credentials: "include",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });
  };

  let response = await oneAttempt();

  // Single silent refresh + retry.
  if (response.status === 401 && !path.includes("/auth/")) {
    const refreshed = await fetch("/api/v1/auth/refresh", { method: "POST", credentials: "include" });
    if (refreshed.ok) {
      response = await oneAttempt();
    }
  }

  if (!response.ok) {
    let body: ApiErrorBody = { status: response.status, code: "", message: "" };
    try {
      body = await response.json();
    } catch {
      /* non-JSON error body */
    }
    // The parsed body (including TASK_VERSION_CONFLICT.currentState, SPEC §7.2)
    // is exposed so callers can reconcile against the server's current state.
    throw new ApiError(response.status, body.code ?? "", body.message ?? `HTTP ${response.status}`, body);
  }

  const text = await response.text();
  return (text ? (JSON.parse(text) as T) : (undefined as T));
}

export const get = <T>(path: string): Promise<T> => api<T>(path);
export const post = <T>(path: string, body?: unknown, idempotent = true): Promise<T> =>
  api<T>(path, { method: "POST", body, idempotent });
export const patch = <T>(path: string, body?: unknown): Promise<T> =>
  api<T>(path, { method: "PATCH", body });
export const del = <T>(path: string): Promise<T> => api<T>(path, { method: "DELETE" });