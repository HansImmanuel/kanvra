"use client";

import { useEffect, useState } from "react";
import { realtime } from "@/lib/websocket";

/**
 * Coarse realtime connection indicator for the board toolbar. Mirrors the
 * shared RealtimeService status without owning any socket logic: "Live" while
 * the socket (or polling fallback) is up, "Reconnecting…" while down, and
 * nothing until the first status is known.
 */
export default function ConnectionBadge() {
  const [up, setUp] = useState<boolean | null>(() => (realtime.isUp() ? true : null));

  useEffect(() => {
    const cb = (v: boolean) => setUp(v);
    realtime.onStatusChange(cb);
    return () => {
      realtime.offStatusChange(cb);
    };
  }, []);

  if (up == null) return null;

  return (
    <span
      role="status"
      className={
        "inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-medium " +
        (up
          ? "border-positive/30 bg-positive/10 text-positive"
          : "border-caution/30 bg-caution/10 text-caution")
      }
    >
      <span
        aria-hidden="true"
        className={`size-1.5 rounded-full ${
          up ? "bg-positive" : "animate-pulse bg-caution motion-reduce:animate-none"
        }`}
      />
      {up ? "Live" : "Reconnecting…"}
    </span>
  );
}
