"use client";

import { useState } from "react";
import { post } from "@/lib/api";
import TaskCard from "@/components/task/TaskCard";
import type { BoardDetail, TaskCard as TaskCardType } from "@/types";

interface BoardProps {
  board: BoardDetail;
  /** Reloads the board from the server (after a mutation). */
  onReload: () => void;
}

/**
 * Renders a Kanban board (docs/TECH_DOC.md §14 "Board state"). Mutations call
 * the backend with the task's known version; the authoritative state is
 * re-fetched afterwards. Optimistic UI + reconcile and the realtime update come
 * in Tasklist 6.
 */
export default function Board({ board, onReload }: BoardProps) {
  const [titles, setTitles] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);

  const createTask = async (columnId: number) => {
    const title = (titles[columnId] ?? "").trim();
    if (!title) return;
    setBusy(true);
    try {
      await post(`/api/v1/columns/${columnId}/tasks`, { title });
      setTitles((t) => ({ ...t, [columnId]: "" }));
      onReload();
    } finally {
      setBusy(false);
    }
  };

  const moveTask = async (task: TaskCardType, targetColumnId: number, position: number) => {
    setBusy(true);
    try {
      await post(`/api/v1/tasks/${task.id}/move`, {
        targetColumnId,
        position,
        version: task.version
      });
      onReload();
    } finally {
      setBusy(false);
    }
  };

  const columnIndex = (id: number) => board.columns.findIndex((c) => c.id === id);
  const appendPos = (columnId: number) =>
    board.columns.find((c) => c.id === columnId)?.tasks.length ?? 0;

  return (
    <div className="flex gap-4 overflow-x-auto p-4" data-testid="board">
      {board.columns.map((column) => {
        const idx = columnIndex(column.id);
        const nextCol = board.columns[idx + 1];
        const prevCol = board.columns[idx - 1];
        return (
          <div key={column.id} className="flex flex-col w-72 min-w-72 gap-2">
            <h2 className="rounded bg-slate-200 px-3 py-2 font-semibold text-slate-700">
              {column.name}
            </h2>
            {column.tasks.map((task) => (
              <div key={task.id} className="flex">
                {prevCol && (
                  <button
                    className="text-sm px-2 text-slate-400 hover:text-slate-700"
                    disabled={busy}
                    onClick={() => moveTask(task, prevCol.id, 0)}
                    aria-label={`move ${task.title} to ${prevCol.name}`}
                  >
                    ←
                  </button>
                )}
                <TaskCard task={task} onMove={moveTask} />
                {nextCol && (
                  <button
                    className="text-sm px-2 text-slate-400 hover:text-slate-700"
                    disabled={busy}
                    onClick={() => moveTask(task, nextCol.id, appendPos(nextCol.id))}
                    aria-label={`move ${task.title} to ${nextCol.name}`}
                  >
                    →
                  </button>
                )}
              </div>
            ))}
            <div className="mt-1">
              <input
                className="w-full rounded border border-slate-300 px-2 py-1 text-sm"
                placeholder="Add a card"
                value={titles[column.id] ?? ""}
                onChange={(e) => setTitles((t) => ({ ...t, [column.id]: e.target.value }))}
                onKeyDown={(e) => {
                  if (e.key === "Enter") void createTask(column.id);
                }}
              />
              <button
                className="mt-1 w-full rounded bg-slate-700 px-2 py-1 text-sm text-white"
                disabled={busy}
                onClick={() => void createTask(column.id)}
              >
                + Add
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}