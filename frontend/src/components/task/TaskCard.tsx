"use client";

import { memo } from "react";
import {
  ArrowDown,
  ArrowRight,
  ArrowUp,
  CalendarClock,
  MessageSquare,
  TriangleAlert
} from "lucide-react";
import type { TaskCard } from "@/types";
import { Avatar, Badge, LabelChip } from "@/components/ui";

/**
 * Maps backend priority enums onto token tones plus a redundant glyph, so
 * priority is never conveyed by color alone.
 */
const PRIORITY_STYLES: Record<
  string,
  { tone: "low" | "medium" | "high" | "critical"; icon: typeof ArrowUp }
> = {
  LOW: { tone: "low", icon: ArrowDown },
  MEDIUM: { tone: "medium", icon: ArrowRight },
  HIGH: { tone: "high", icon: ArrowUp },
  CRITICAL: { tone: "critical", icon: TriangleAlert }
};

/** Morning-of counts as on time; pure client-side display logic. */
function isOverdue(iso: string): boolean {
  const due = new Date(iso);
  const today = new Date();
  due.setHours(0, 0, 0, 0);
  today.setHours(0, 0, 0, 0);
  return due.getTime() < today.getTime();
}

function formatDueDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

interface TaskCardProps {
  task: TaskCard;
  /** Optional: makes the title clickable to open the task detail modal. */
  onSelect?: (task: TaskCard) => void;
  /** True while an optimistic move of this card is in flight (ghost styling). */
  moving?: boolean;
}

/** A task card; shows priority, labels, assignee, due date, comments. Memoized: */
function TaskCard({ task, onSelect, moving = false }: TaskCardProps) {
  const style = task.priority != null ? PRIORITY_STYLES[task.priority] : undefined;
  const overdue = task.dueDate != null && isOverdue(task.dueDate);

  return (
    <article
      className={
        "rounded-panel border bg-surface p-3 shadow-card " +
        "transition-[border-color,box-shadow] duration-150 hover:border-edge-strong hover:shadow-md motion-reduce:transition-none " +
        (moving ? "border-primary/40 opacity-50" : "border-edge")
      }
    >
      <div className="flex items-start justify-between gap-2">
        <button
          type="button"
          className={
            "min-w-0 flex-1 rounded py-0.5 text-left text-sm font-medium text-body " +
            "hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          }
          onClick={() => onSelect?.(task)}
        >
          {task.title}
        </button>
        {task.priority != null && style != null && (
          <Badge tone={style.tone} icon={<style.icon size={11} aria-hidden="true" />}>
            {task.priority}
          </Badge>
        )}
      </div>

      {task.labels?.length > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1">
          {task.labels.map((label) => (
            <LabelChip key={label.id} name={label.name} color={label.color} />
          ))}
        </div>
      )}

      <div className="mt-2.5 flex items-center gap-2.5 text-xs text-muted">
        {task.assignee != null && (
          <span title={`Assignee: ${task.assignee.name}`} className="shrink-0">
            <Avatar name={task.assignee.name} avatarUrl={task.assignee.avatarUrl} size={20} />
          </span>
        )}
        {task.dueDate != null && (
          <span
            className={
              "inline-flex items-center gap-1 " +
              (overdue ? "font-medium text-red-700" : "text-muted")
            }
            title={overdue ? "Overdue" : "Due date"}
          >
            <CalendarClock size={12} aria-hidden="true" />
            {formatDueDate(task.dueDate)}
          </span>
        )}
        {task.commentCount > 0 && (
          <span
            className="inline-flex items-center gap-1"
            title={`${task.commentCount} comments`}
          >
            <MessageSquare size={12} aria-hidden="true" />
            {task.commentCount}
          </span>
        )}
        <span
          className="ml-auto shrink-0 rounded bg-elevated px-1 py-px text-[10px] font-medium text-muted"
          title={`Version ${task.version}`}
        >
          v{task.version}
        </span>
      </div>
    </article>
  );
}

export default memo(TaskCard);
