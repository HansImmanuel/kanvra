"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { listNotifications, markAllNotificationsRead, markNotificationRead } from "@/lib/actions";
import { realtime } from "@/lib/websocket";
import { relativeTime } from "@/lib/time";
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

  const unread = items.filter((n) => !n.readAt).length;

  const openItem = async (n: Notification) => {
    setBusy(true);
    try {
      if (!n.readAt) await markNotificationRead(n.id);
      await reload();
      if (n.projectId != null) {
        setOpen(false);
        router.push(`/projects/${n.projectId}`);
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
      <button
        type="button"
        aria-label={`Notifications${unread ? ` (${unread} unread)` : ""}`}
        aria-expanded={open}
        className="relative rounded px-2 py-1 text-lg hover:bg-slate-100"
        onClick={() => setOpen((v) => !v)}
      >
        🔔
        {unread > 0 && (
          <span
            data-testid="notif-badge"
            className="absolute -right-1 -top-1 inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-semibold text-white"
          >
            {unread}
          </span>
        )}
      </button>

      {open && (
        <div
          data-testid="notif-panel"
          className="absolute right-0 z-50 mt-2 w-80 rounded-lg border border-slate-300 bg-white p-2 shadow-xl"
        >
          <div className="mb-1 flex items-center justify-between px-1">
            <p className="text-sm font-semibold text-slate-700">Notifications</p>
            <button
              type="button"
              disabled={busy || unread === 0}
              className="rounded px-1 text-xs text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40"
              onClick={() => void markAll()}
            >
              Mark all read
            </button>
          </div>

          <ul className="max-h-80 space-y-1 overflow-y-auto">
            {items.length === 0 && (
              <li className="px-2 py-3 text-sm text-slate-400">No notifications yet.</li>
            )}
            {items.map((n) => (
              <li key={n.id}>
                <button
                  type="button"
                  disabled={busy}
                  className={
                    "w-full rounded p-2 text-left text-sm hover:bg-slate-50 " +
                    (n.readAt ? "text-slate-500" : "bg-indigo-50 text-slate-800")
                  }
                  onClick={() => void openItem(n)}
                >
                  <span className="flex items-start gap-2">
                    {!n.readAt && <span aria-hidden="true" className="mt-1 h-2 w-2 shrink-0 rounded-full bg-indigo-500" />}
                    <span className="flex-1">{n.message}</span>
                    <span className="text-xs text-slate-400">{relativeTime(n.createdAt)}</span>
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