// Realtime client (docs/TECH_DOC.md §14 lib/websocket, SPEC §15).
//
// STOMP over the same-origin /ws endpoint. The browser attaches the httpOnly
// session cookies to the WebSocket handshake automatically (SPEC §15.1) — no
// token is read or stored in JS. Query-string tokens are never used.
//
// - Own mutation eventIds are tracked so their echoes are ignored (SPEC §15.3
//   local-echo dedup); remote events are forwarded to listeners.
// - A dropped connection reconnects with backoff and re-subscribes to
//   /topic/projects/{projectId}. Because WebSocket delivery has NO replay
//   guarantee, an onResync hook is fired so the caller re-fetches the board via
//   GET /api/v1/boards/{boardId} and replaces local state (SPEC §15.2 —
//   required behavior, not an optimization).

import { Client } from "@stomp/stompjs";
import type { IMessage } from "@stomp/stompjs";

export type RealtimeEvent = {
  type: string;
  eventId: string;
  projectId: number;
  payload?: Record<string, unknown>;
};

type RealtimeListener = (event: RealtimeEvent) => void;

/// Module-level singleton so the board page and its components share one STOMP
/// connection rather than opening a socket per component.
class RealtimeService {
  private client: Client | null = null;
  private projectId: number | null = null;
  private connected = false;
  private listeners = new Set<RealtimeListener>();
  private resyncCallbacks = new Set<() => void>();
  private ownEventIds = new Set<string>();

  on(callback: RealtimeListener): void {
    this.listeners.add(callback);
  }

  off(callback: RealtimeListener): void {
    this.listeners.delete(callback);
  }

  /** Registers a callback to re-fetch the board on (re)connect. */
  onResync(callback: () => void): void {
    this.resyncCallbacks.add(callback);
    if (this.connected) callback();
  }

  offResync(callback: () => void): void {
    this.resyncCallbacks.delete(callback);
  }

  /** Records a local mutation eventId so its echo is dropped. */
  noteOwnEventId(eventId: string | null | undefined): void {
    if (!eventId) return;
    this.ownEventIds.add(eventId);
    // Bounded memory: cap the seen set.
    if (this.ownEventIds.size > 500) {
      const evicted = this.ownEventIds.values().next()?.value;
      if (evicted !== undefined) this.ownEventIds.delete(evicted);
    }
  }

  connect(projectId: number): void {
    if (this.client && this.projectId === projectId && this.connected) return;
    this.projectId = projectId;
    this.open();
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.connected = false;
  }

  private open(): void {
    // Next dev does not proxy WebSocket upgrades, so the STOMP client connects
    // straight to the backend's /ws endpoint. In dev that's localhost:8080
    // (same-site as the Next app on :3000, so SameSite=Lax cookies still attach
    // to the handshake). Override with NEXT_PUBLIC_WS_URL behind a real gateway.
    const configured = process.env.NEXT_PUBLIC_WS_URL;
    const fallback = (window.location.protocol === "https:" ? "wss://" : "ws://") + "localhost:8080/ws";
    const url = configured && configured.trim().length > 0 ? configured : fallback;

    this.client = new Client({
      brokerURL: url,
      reconnectDelay: 3000,
      maxReconnectDelay: 30000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000
    });

    this.client.onConnect = () => {
      this.connected = true;
      // Messages on /topic/projects/{projectId} arrive here; handle() dedups
      // local echoes and forwards remote events to listeners. Storing the
      // subscription keeps the callback set for the lifetime of the connection.
      this.client?.subscribe(`/topic/projects/${this.projectId}`, (message: IMessage) => {
        this.handle(message.body);
      });
      // Fire resync: delivery has no replay guarantee, so always re-fetch.
      this.resyncCallbacks.forEach((cb) => cb());
    };

    this.client.onDisconnect = () => {
      this.connected = false;
    };

    this.client.onWebSocketClose = () => {
      this.connected = false;
    };

    this.client.activate();
  }

  private handle(body: string | undefined): void {
    if (!body) return;
    let event: RealtimeEvent;
    try {
      event = JSON.parse(body) as RealtimeEvent;
    } catch {
      return; // ignore control/keepalive frames
    }
    if (!event?.type || !event.eventId) return;
    if (this.ownEventIds.delete(event.eventId)) return; // own echo -> ignore
    for (const listener of this.listeners) listener(event);
  }
}

/** Shared realtime service instance. */
export const realtime = new RealtimeService();

/** Records a local mutation eventId via the shared service. */
export const noteLocalEventId = (eventId: string | null | undefined): void =>
  realtime.noteOwnEventId(eventId);