// Realtime client (docs/TECH_DOC.md §14 lib/websocket, SPEC §15).
//
// Placeholder that establishes the directory structure needed by TECH_DOC §14.
// The STOMP connect/subscribe + reconnect-resync wiring lands in Tasklist 6.
export type RealtimeEvent = {
  type: string;
  eventId: string;
  projectId: number;
  payload?: Record<string, unknown>;
};

export interface RealtimeClient {
  connect(projectId: number): void;
  disconnect(): void;
  on(callback: (event: RealtimeEvent) => void): void;
}

/** No-op client until the WebSocket/STOMP implementation is added (Tasklist 6). */
export class RealtimeClientStub implements RealtimeClient {
  private listeners: ((event: RealtimeEvent) => void)[] = [];

  connect() {}
  disconnect() {}
  on(callback: (event: RealtimeEvent) => void) {
    this.listeners.push(callback);
  }
}