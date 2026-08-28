"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Activity as ActivityIcon,
  ArrowRight,
  MessageSquare,
  Plus,
  Tag,
  Trash2,
  Users,
  type LucideIcon
} from "lucide-react";
import { listActivity } from "@/lib/actions";
import { realtime } from "@/lib/websocket";
import { Button, ErrorState, Skeleton } from "@/components/ui";
import type { Activity } from "@/types";

interface ActivityPanelProps {
  projectId: number;
}

/**
 * Event-type → icon mapping. The activity payload carries no actor name or
 * avatar URL (by design — SPEC §11), so the "avatar" slot is an event-type
 * chip: the human context lives in the message text itself.
 */
const TYPE_ICONS: { match: string; icon: LucideIcon }[] = [
  { match: "TASK_CREATED", icon: Plus },
  { match: "TASK_MOVED", icon: ArrowRight },
  { match: "TASK_DELETED", icon: Trash2 },
  { match: "COMMENT", icon: MessageSquare },
  { match: "LABEL", icon: Tag },
  { match: "MEMBER", icon: Users }
];

function iconFor(type: string): LucideIcon {
  const hit = TYPE_ICONS.find((t) => type.toUpperCase().startsWith(t.match));
  return hit?.icon ?? ActivityIcon;
}

/** Local calendar-day label (pure client-side display logic). */
function dayLabel(iso: string): string {
  const d = new Date(iso);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  const same = (a: Date, b: Date) => a.toDateString() === b.toDateString();
  if (same(d, today)) return "Today";
  if (same(d, yesterday)) return "Yesterday";
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

interface DayGroup {
  label: string;
  items: Activity[];
}

/** Groups items into adjacent day buckets (server order is newest-first). */
function groupByDay(items: Activity[]): DayGroup[] {
  const groups: DayGroup[] = [];
  for (const item of items) {
    const label = dayLabel(item.createdAt);
    const last = groups.at(-1);
    if (last && last.label === label) {
      last.items.push(item);
    } else {
      groups.push({ label, items: [item] });
    }
  }
  return groups;
}

/**
 * Project activity feed (docs/SPEC.md §11, PRD "Activity panel"). Rows are
 * written asynchronously by the Kafka Activity Consumer — this panel is the
 * visible proof of that pipeline. Rendered inside the board's modal: the modal
 * owns the surface chrome, so this component stays chrome-free. Any realtime
 * domain event triggers an authoritative re-fetch.
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

  // Skeleton matches the feed-item geometry (chip + message + timestamp).
  if (loading) {
    return (
      <div
        data-testid="activity-panel"
        aria-busy="true"
        aria-label="Loading activity"
        className="space-y-4 px-1 pb-1"
      >
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="flex items-start gap-2.5">
            <Skeleton className="size-7 shrink-0 rounded-full" />
            <div className="min-w-0 flex-1 space-y-1.5 pt-1">
              <Skeleton className="h-3 w-3/4" />
              <Skeleton tone="edge" className="h-2.5 w-20" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div data-testid="activity-panel" className="animate-kv-fade-in">
      {error && <ErrorState message={error} onRetry={() => void load(0)} className="mb-3" />}

      {items.length === 0 ? (
        <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
          <span
            aria-hidden="true"
            className="flex size-10 items-center justify-center rounded-full bg-primary/10 text-primary"
          >
            <ActivityIcon size={18} />
          </span>
          <p className="text-sm font-medium text-heading">No activity yet</p>
          <p className="max-w-56 text-xs text-faint">
            Create or move a task and it will show up here in real time.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {groupByDay(items).map((group) => (
            <section key={group.label}>
              <h3 className="mb-1.5 flex items-center gap-2 text-[11px] font-medium uppercase tracking-wide text-muted">
                {group.label}
                <span aria-hidden="true" className="h-px flex-1 bg-edge" />
              </h3>
              <ul className="space-y-0.5">
                {group.items.map((a) => {
                  const Icon = iconFor(a.type);
                  return (
                    <li
                      key={a.id}
                      className="flex items-start gap-2.5 rounded-panel px-2 py-1.5 transition-colors hover:bg-elevated motion-reduce:transition-none"
                    >
                      <span
                        aria-hidden="true"
                        className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary"
                      >
                        <Icon size={13} />
                      </span>
                      <p className="min-w-0 flex-1 text-sm leading-relaxed text-body">{a.message}</p>
                      <time
                        dateTime={a.createdAt}
                        className="shrink-0 pt-0.5 text-xs text-muted"
                        title={new Date(a.createdAt).toLocaleString()}
                      >
                        {clockTime(a.createdAt)}
                      </time>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
      )}

      {page + 1 < totalPages && (
        <Button variant="outline" size="sm" className="mt-4" onClick={() => void load(page + 1)}>
          Load more
        </Button>
      )}
    </div>
  );
}