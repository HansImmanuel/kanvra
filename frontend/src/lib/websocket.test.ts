import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  RealtimeService,
  realtime,
  noteLocalEventId,
  FALLBACK_AFTER_MS,
  FALLBACK_POLL_MS,
  SOCKET_RECHECK_MS
} from "./websocket";

/**
 * Realtime client contract (docs/SPEC.md §15, TECH_DOC.md §14): the service
 * exposes resync hooks that only fire once the socket is up, tracks local
 * mutation eventIds for echo-dedup, and degrades to an authoritative REST
 * polling fallback when the WebSocket is unreachable. These tests pin the
 * parts that don't require a live STOMP connection.
 */
describe("realtime service", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    realtime.disconnect();
  });

  it("does not fire resync callbacks before a connection is established", () => {
    const service = new RealtimeService();
    const cb = vi.fn();
    service.onResync(cb);
    service.offResync(cb);
    expect(cb).not.toHaveBeenCalled();
  });

  it("isUp is false while never connected and no fallback is engaged", () => {
    const service = new RealtimeService();
    expect(service.isUp()).toBe(false);
  });

  it("exposes the documented polling-fallback timings", () => {
    // SPEC §15.2: reconnecting is not enough — the client must re-fetch the
    // board; the fallback poll IS that authoritative recovery at a sane cadence.
    expect(FALLBACK_AFTER_MS).toBeGreaterThan(0);
    expect(FALLBACK_POLL_MS).toBeGreaterThan(0);
    expect(SOCKET_RECHECK_MS).toBeGreaterThan(FALLBACK_POLL_MS);
  });

  it("records local event ids for echo dedup without throwing", () => {
    const service = new RealtimeService();
    for (let i = 0; i < 600; i++) service.noteOwnEventId(`evt-${i}`);
    service.noteOwnEventId(null);
    service.noteOwnEventId(undefined);
    // The singleton helper delegates to the shared instance.
    noteLocalEventId("evt-singleton");
    expect(realtime.isUp()).toBe(false);
  });
});