"use client";

import { useCallback, useEffect, useState } from "react";
import { listActivity } from "@/lib/actions";
import { realtime } from "@/lib/websocket";
import { relativeTime } from "@/lib/time";
import { Button } from "@/components/ui";
import type { Activity } from "@/types";

interface ActivityPanelProps {
  projectId: number;
}

/**
 * Project activity feed (docs/SPEC.md §11, PRD "Activity panel"). Rows are
 * written asynchronously by the Kafka Activity Consumer — this panel is the
 * visible proof of that pipeline (PRD principle: the UI demonstrates the
 * architecture). Any realtime domain event triggers an authoritative re-fetch.
 */
export default function ActivityPanel({ projectId }: ActivityPanelProps) {
  const [items, setItems] = useState<Activity[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (targetPage = 0) => {
      try {
        const result = await listActivity(projectId, targetPage);
        setItems((prev) => (targetPage === 0 ? result.content : [...prev, ...result.content]));
        setPage(result.page);
        setTotalPages(result.totalPages);
        setError(null);
      } catch {
        setError("Failed to load activity");
      }
    },
    [projectId]
  );

  useEffect(() => {
    setLoading(true);
    void load(0).finally(() => setLoading(false));
  }, [load]);

  // All domain events are project-scoped; any of them may add activity rows.
  useEffect(() => {
    const handler = () => void load(0);
    realtime.on(handler as never);
    return () => {
      realtime.off(handler as never);
    };
  }, [load]);

  return (
    <section data-testid="activity-panel" className="rounded-lg border border-slate-300 bg-white p-4">
      <h2 className="mb-2 text-sm font-semibold text-slate-700">Activity</h2>

      {loading ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : (
        <>
          <ul className="space-y-1">
            {items.length === 0 && (
              <li className="text-sm text-slate-400">No activity yet — create or move a task.</li>
            )}
            {items.map((a) => (
              <li key={a.id} className="flex items-baseline gap-2 text-sm">
                <span className="text-slate-700">{a.message}</span>
                <span className="ml-auto shrink-0 text-xs text-slate-400">{relativeTime(a.createdAt)}</span>
              </li>
            ))}
          </ul>

          {page + 1 < totalPages && (
            <Button variant="outline" size="sm" className="mt-2" onClick={() => void load(page + 1)}>
              Load more
            </Button>
          )}

          {error && <p className="mt-2 rounded bg-red-100 border border-red-300 p-2 text-xs">{error}</p>}
        </>
      )}
    </section>
  );
}