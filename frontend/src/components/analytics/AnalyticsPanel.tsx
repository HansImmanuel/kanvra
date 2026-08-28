"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ArrowRight,
  Check,
  ChevronRight,
  MessageSquare,
  Plus,
  Trash2,
  type LucideIcon
} from "lucide-react";
import { getProjectAnalytics } from "@/lib/actions";
import { realtime } from "@/lib/websocket";
import { colors } from "@/lib/design-tokens";
import { ErrorState, Skeleton } from "@/components/ui";
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

  const toggleLabel =
    "flex items-center gap-2 text-sm font-semibold text-heading transition-colors " +
    "hover:text-body focus-visible:outline-2 focus-visible:outline-offset-2 " +
    "focus-visible:outline-accent motion-reduce:transition-none";

  // Skeletons mirror the loaded layout: five stat tiles + three bar rows.
  if (loading) {
    return (
      <section data-testid="analytics-panel" aria-busy="true" aria-label="Loading analytics" className="rounded-panel border border-edge-strong bg-surface shadow-card">
        <div className="flex items-center gap-2 px-4 py-3">
          <Skeleton className="h-3.5 w-24" />
        </div>
        <div className="space-y-3 border-t border-edge px-4 pb-4 pt-3">
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
            {[0, 1, 2, 3, 4].map((i) => (
              <div key={i} className="rounded-panel border border-edge bg-elevated p-2.5">
                <Skeleton className="h-2 w-14" />
                <Skeleton className="mt-2 h-5 w-8" />
              </div>
            ))}
          </div>
          <div className="space-y-2 pt-1">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} tone="edge" className="h-2.5 rounded-full" />
            ))}
          </div>
        </div>
      </section>
    );
  }

  return (
    <section data-testid="analytics-panel" className="rounded-panel border border-edge-strong bg-surface shadow-card">
      <header className="flex items-center justify-between gap-3 px-4 py-3">
        <button
          type="button"
          aria-expanded={open}
          aria-controls="analytics-details"
          className={toggleLabel}
          onClick={() => setOpen((prev) => !prev)}
        >
          <span className={open ? "inline-block transition-transform rotate-90 motion-reduce:transition-none" : "inline-block transition-transform motion-reduce:transition-none"}>
            <ChevronRight size={14} aria-hidden="true" />
          </span>
          Analytics
        </button>
        {analytics != null && !error && (
          <span className="shrink-0 text-xs text-faint">
            {totalCards} active {totalCards === 1 ? "card" : "cards"}
          </span>
        )}
      </header>

      {error && <ErrorState message={error} onRetry={() => void load()} className="mx-4 mb-3" />}

      {analytics && (
        // Rendered once data is present, but hidden until expanded (SPEC §12.5).
        <div
          id="analytics-details"
          style={{ display: open ? "block" : "none" }}
          data-testid="analytics-details"
          className="border-t border-edge px-4 pb-4 pt-3"
        >
          <div className="mb-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
            <Stat label="Created" value={counters?.tasksCreated ?? 0} icon={Plus} />
            <Stat label="Completed" value={counters?.tasksCompleted ?? 0} icon={Check} />
            <Stat label="Moved" value={counters?.tasksMoved ?? 0} icon={ArrowRight} />
            <Stat label="Deleted" value={counters?.tasksDeleted ?? 0} icon={Trash2} />
            <Stat label="Comments" value={counters?.commentsCreated ?? 0} icon={MessageSquare} />
          </div>

          <p className="mb-1.5 text-xs text-muted">
            Cards per column <span className="text-faint">· {totalCards} active</span>
          </p>
          <ul className="space-y-1.5">
            {analytics.cardsPerColumn.length === 0 && (
              <li className="text-sm text-faint">
                No columns yet — create a column on the board to see its card count here.
              </li>
            )}
            {analytics.cardsPerColumn.map((col) => {
              const pct = Math.max(0, Math.round((col.count / maxCount(analytics)) * 100));
              return (
                <li key={col.columnId} className="flex items-center gap-3 text-sm">
                  <span className="w-28 shrink-0 truncate text-body" title={col.columnName}>
                    {col.columnName}
                  </span>
                  <div
                    role="img"
                    aria-label={`${col.count} cards in ${col.columnName}`}
                    title={`${col.count} of ${totalCards} active cards (${pct}%)`}
                    className="h-2.5 flex-1 overflow-hidden rounded-full bg-elevated"
                  >
                    <span
                      className="block h-full rounded-full"
                      style={{ width: `${pct}%`, backgroundColor: colors.chartBar }}
                    />
                  </div>
                  <span className="w-8 shrink-0 text-right text-xs font-medium text-muted">
                    {col.count}
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </section>
  );
}

function Stat({
  label,
  value,
  icon: Icon
}: {
  label: string;
  value: number;
  icon: LucideIcon;
}) {
  return (
    <div className="rounded-panel border border-edge bg-elevated p-2.5">
      <div className="flex items-center gap-1.5 text-faint">
        <Icon size={12} aria-hidden="true" />
        <p className="text-[11px] font-medium uppercase tracking-wide">{label}</p>
      </div>
      <p className="mt-1 text-lg font-semibold text-heading">{value}</p>
    </div>
  );
}

function maxCount(analytics: ProjectAnalytics): number {
  return Math.max(1, ...analytics.cardsPerColumn.map((c) => c.count));
}