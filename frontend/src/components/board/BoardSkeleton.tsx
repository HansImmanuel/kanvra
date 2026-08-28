"use client";

import { Skeleton } from "@/components/ui";

/**
 * Board-shaped loading skeleton: three column wells, each with a header
 * (title bar + count chip), two task cards (title bar + avatar/meta row), and
 * the add-card footer — matching the live board geometry to prevent layout
 * shift when data arrives.
 */
export default function BoardSkeleton() {
  return (
    <div
      aria-busy="true"
      aria-label="Loading board"
      className="flex gap-4 overflow-hidden p-4 pb-6"
    >
      {[0, 1, 2].map((column) => (
        <div
          key={column}
          className="flex w-72 min-w-72 shrink-0 flex-col gap-2 rounded-panel border border-edge bg-elevated/60 p-2"
        >
          <div className="flex items-center justify-between px-1 py-1">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="size-7 rounded-panel" />
          </div>

          {[0, 1].map((task) => (
            <div
              key={task}
              className="rounded-panel border border-edge bg-surface p-3 shadow-card"
            >
              <Skeleton className="h-4 w-3/4" />
              <div className="mt-2.5 flex items-center gap-2.5">
                <Skeleton className="size-5 rounded-full" />
                <Skeleton className="h-2.5 w-14" />
              </div>
            </div>
          ))}

          <div className="mt-1 border-t border-edge pt-2">
            <Skeleton className="h-8 w-full rounded-field" />
          </div>
        </div>
      ))}
    </div>
  );
}
