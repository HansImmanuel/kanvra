"use client";

import type { TaskCard } from "@/types";
import { Badge } from "@/components/ui";

/** Maps backend priority enums onto the shared design-token status palette. */
const PRIORITY_TONES: Record<string, "low" | "medium" | "high" | "critical"> = {
  LOW: "low",
  MEDIUM: "medium",
  HIGH: "high",
  CRITICAL: "critical"
};

interface TaskCardProps {
  task: TaskCard;
  /** Optional: makes the title clickable to open the task detail modal. */
  onSelect?: (task: TaskCard) => void;
}

/** A single task card; shows labels/priority, move controls live in Board. */
export default function TaskCard({ task, onSelect }: TaskCardProps) {
  const priority = task.priority ?? "NONE";
  const titleButton = (
    <button
      type="button"
      className="text-left font-medium text-slate-800 hover:text-slate-600 hover:underline focus:outline-none"
      onClick={() => onSelect?.(task)}
    >
      {task.title}
    </button>
  );
  return (
    <div className="rounded-lg border border-slate-300 bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <p className="font-medium text-slate-800">{titleButton}</p>
        {task.priority && (
          <Badge tone={PRIORITY_TONES[priority] ?? "none"}>{task.priority}</Badge>
        )}
      </div>
      {task.labels?.length > 0 && (
        <div className="mt-1 flex flex-wrap gap-1">
          {task.labels.map((label) => (
            <span key={label.id} className="rounded bg-slate-100 px-1.5 py-0.5 text-xs">{label.name}</span>
          ))}
        </div>
      )}
      <div className="mt-2 text-xs text-slate-500">
        {task.commentCount > 0 && <>💬 {task.commentCount} · </>}
        v{task.version}
      </div>
    </div>
  );
}