"use client";

import type { TaskCard } from "@/types";

const PRIORITY_COLORS: Record<string, string> = {
  HIGH: "bg-red-200 text-red-800",
  MEDIUM: "bg-amber-200 text-amber-800",
  LOW: "bg-green-200 text-green-800",
  CRITICAL: "bg-rose-300 text-rose-900"
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
          <span
            className={"inline-block rounded px-1.5 py-0.5 text-xs " + (PRIORITY_COLORS[priority] ?? "bg-slate-200")}
          >
            {task.priority}
          </span>
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