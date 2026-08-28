"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Bell } from "lucide-react";
import { listNotifications, markAllNotificationsRead, markNotificationRead } from "@/lib/actions";
import { realtime } from "@/lib/websocket";
import { relativeTime } from "@/lib/time";
import { Button, Tooltip } from "@/components/ui";
import type { Notification } from "@/types";

/**
 * Header notification bell + dropdown panel (docs/SPEC.md §12, PRD
 * "Notification panel").
 *
 * - Unread badge is computed from the newest page (size 20 — fine at MVP scale).
 * - Clicking an item marks it read and, when the denormalized projectId is
 *   present, deep-links to that project's board.
 * - Any realtime domain event may produce notifications, so the badge refreshes
 *   on the shared stream too.
 */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<Notification[]>([]);
  const [busy, setBusy] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const router = useRouter();

  const reload = useCallback(async () => {
    try {
      const page = await listNotifications(0, 20);
      setItems(page.content);
    } catch {
      /* badge widget: silent degradation */
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    const handler = () => void reload();
    realtime.on(handler as never);
    return () => {
      realtime.off(handler as never);
    };
  }, [reload]);

  // Close on outside click.
  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [open]);

  // Close on Escape (keyboard parity with the modal surfaces).
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  const unread = items.filter((n) => !n.readAt).length;

  const openItem = async (n: Notification) => {
    setBusy(true);
    try {
      if (!n.readAt) await markNotificationRead(n.id);
      await reload();
      if (n.projectId != null) {
        setOpen(false);
        router.push(`/projects/${n.projectId}/board`);
      }
    } finally {
      setBusy(false);
    }
  };

  const markAll = async () => {
    setBusy(true);
    try {
      await markAllNotificationsRead();
      await reload();
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="relative" ref={wrapRef}>
      <Tooltip label="Notifications">
        <button
          type="button"
          aria-label={`Notifications${unread ? ` (${unread} unread)` : ""}`}
          aria-expanded={open}
          className="relative flex size-9 items-center justify-center rounded-panel text-body transition-colors hover:bg-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent motion-reduce:transition-none"
          onClick={() => setOpen((v) => !v)}
        >
          <Bell size={17} aria-hidden="true" />
          {unread > 0 && (
            <span
              data-testid="notif-badge"
              className="absolute -right-0.5 -top-0.5 inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white"
            >
              {unread}
            </span>
          )}
        </button>
      </Tooltip>

      {open && (
        <div
          data-testid="notif-panel"
          className="absolute right-0 z-50 mt-2 w-80 rounded-panel border border-edge bg-surface p-2 shadow-overlay"
        >
          <div className="mb-2 flex items-center justify-between border-b border-edge px-1 pb-2">
            <p className="text-sm font-semibold text-heading">Notifications</p>
            <Button
              variant="link"
              disabled={busy || unread === 0}
              onClick={() => void markAll()}
            >
              Mark all read
            </Button>
          </div>

          <ul className="max-h-80 space-y-0.5 overflow-y-auto">
            {items.length === 0 && (
              <li className="px-2 py-3 text-sm text-muted">No notifications yet.</li>
            )}
            {items.map((n) => (
              <li key={n.id}>
                <button
                  type="button"
                  disabled={busy}
                  className={
                    "w-full rounded p-2 text-left text-sm transition-colors hover:bg-elevated focus-visible:outline-2 focus-visible:outline-accent motion-reduce:transition-none disabled:opacity-50 " +
                    (n.readAt ? "text-muted" : "bg-accent/10 text-body")
                  }
                  onClick={() => void openItem(n)}
                >
                  <span className="flex items-start gap-2">
                    {!n.readAt && (
                      <span aria-hidden="true" className="mt-1 h-2 w-2 shrink-0 rounded-full bg-accent" />
                    )}
                    <span className="flex-1">{n.message}</span>
                    <span className="text-xs text-muted">{relativeTime(n.createdAt)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}