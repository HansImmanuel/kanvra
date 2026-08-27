"use client";

import { useCallback, useEffect, useState } from "react";
import { ChevronRight } from "lucide-react";
import { getProjectAnalytics } from "@/lib/actions";
import { realtime } from "@/lib/websocket";
import { colors } from "@/lib/design-tokens";
import type { ProjectAnalytics } from "@/types";

interface AnalyticsPanelProps {
  projectId: number;
}

/**
 * Project analytics summary (docs/PRD.md §6.10, docs/SPEC.md §12.5).
 * Counters are accumulated asynchronously by the Kafka Analytics Consumer
 * (group kanvra-analytics); cards-per-column is derived live from the
 * authoritative board endpoint. Re-fetches on any realtime domain event.
 */
export default function AnalyticsPanel({ projectId }: AnalyticsPanelProps) {
  const [analytics, setAnalytics] = useState<ProjectAnalytics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await getProjectAnalytics(projectId);
      setAnalytics(data);
      setError(null);
    } catch {
      setError("Failed to load analytics");
    }
  }, [projectId]);

  useEffect(() => {
    setLoading(true);
    void load().finally(() => setLoading(false));
  }, [load]);

  // Board/comment events change counters; refetch (like ActivityPanel).
  useEffect(() => {
    const handler = () => void load();
    realtime.on(handler as never);
    return () => {
      realtime.off(handler as never);
    };
  }, [load]);

  const counters = analytics?.counters;
  const totalCards = counters
    ? counters.tasksCreated - counters.tasksDeleted
    : 0;

  return (
    <section data-testid="analytics-panel" className="rounded-lg border border-slate-300 bg-white p-4">
      <h2 className="mb-2 text-sm font-semibold text-slate-700">
        <button
          type="button"
          className="flex items-center gap-2"
          aria-expanded={open}
          onClick={() => setOpen((prev) => !prev)}
        >
          <span className={open ? "rotate-90 inline-block transition-transform" : "inline-block transition-transform"}>
            <ChevronRight size={14} aria-hidden="true" />
          </span>
          Analytics
        </button>
      </h2>

      {loading ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : (
        <>
          {error && <p className="rounded bg-red-100 border border-red-300 p-2 text-xs">{error}</p>}
          {analytics && (
            <div style={{ display: open ? "block" : "none" }} data-testid="analytics-details">
              <div className="mb-3 grid grid-cols-2 gap-2 sm:grid-cols-5">
                <Stat label="Created" value={counters?.tasksCreated ?? 0} />
                <Stat label="Completed" value={counters?.tasksCompleted ?? 0} />
                <Stat label="Moved" value={counters?.tasksMoved ?? 0} />
                <Stat label="Deleted" value={counters?.tasksDeleted ?? 0} />
                <Stat label="Comments" value={counters?.commentsCreated ?? 0} />
              </div>

              <p className="mb-1 text-xs text-slate-500">
                Cards per column ({totalCards} active)
              </p>
              <ul className="space-y-1">
                {analytics.cardsPerColumn.length === 0 && (
                  <li className="text-sm text-slate-400">No columns yet.</li>
                )}
                {analytics.cardsPerColumn.map((col) => (
                  <li key={col.columnId} className="flex items-center gap-2 text-sm">
                    <span className="w-28 shrink-0 truncate text-slate-700">{col.columnName}</span>
                    <span className="h-2 flex-1 overflow-hidden rounded bg-slate-100">
                      <span
                        className="block h-full"
                        style={{
                          width: barWidth(col.count, maxCount(analytics)),
                          backgroundColor: colors.chartBar
                        }}
                      />
                    </span>
                    <span className="w-6 shrink-0 text-right text-xs text-slate-500">{col.count}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </section>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded border border-slate-200 p-2">
      <p className="text-lg font-semibold text-slate-800">{value}</p>
      <p className="text-xs text-slate-500">{label}</p>
    </div>
  );
}

function maxCount(analytics: ProjectAnalytics): number {
  return Math.max(1, ...analytics.cardsPerColumn.map((c) => c.count));
}

function barWidth(count: number, max: number): string {
  return `${Math.max(0, Math.round((count / max) * 100))}%`;
}