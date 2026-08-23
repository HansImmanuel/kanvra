"use client";

import { useState, useEffect } from "react";
import { post, ApiError } from "@/lib/api";
import { realtime, noteLocalEventId } from "@/lib/websocket";
import TaskCard from "@/components/task/TaskCard";
import type { BoardDetail, TaskCard as TaskCardType, TaskResponse } from "@/types";

interface BoardProps {
  board: BoardDetail;
  /** Reloads the board from the server (after a mutation or remote change). */
  onReload: () => void;
}

/**
 * Renders a Kanban board and wires realtime updates (docs/TECH_DOC.md §14
 * "Board state", SPEC §15). Mutations send the task's known version and record
 * their eventId (local echo is ignored on the WS stream). Remote events and
 * any reconnect trigger a full re-fetch (SPEC §15.2 resync), so the server
 * always remains the source of truth.
 */
export default function Board({ board, onReload }: BoardProps) {
  const [titles, setTitles] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);

  const createTask = async (columnId: number) => {
    const title = (titles[columnId] ?? "").trim();
    if (!title) return;
    setBusy(true);
    try {
      const created = await post<TaskResponse>(`/api/v1/columns/${columnId}/tasks`, { title });
      noteLocalEventId(created.eventId);
      setTitles((t) => ({ ...t, [columnId]: "" }));
      onReload();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        onReload(); // server state changed; re-sync before the next action
      }
    } finally {
      setBusy(false);
    }
  };

  const moveTask = async (task: TaskCardType, targetColumnId: number, position: number) => {
    setBusy(true);
    try {
      const moved = await post<TaskResponse>(`/api/v1/tasks/${task.id}/move`, {
        targetColumnId,
        position,
        version: task.version
      });
      noteLocalEventId(moved.eventId);
      onReload();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // TASK_VERSION_CONFLICT — this card's version is stale. Re-fetch the board
        // so the next action uses the server's current version instead of a loop of
        // repeat 409s until a manual refresh (SPEC §7.2 + §15 resync).
        onReload();
      }
    } finally {
      setBusy(false);
    }
  };

  // Connect to realtime for this board's project; re-load on reconnect and when
  // a remote (non-local-echo) event arrives.
  useEffect(() => {
    if (!board?.projectId) return;
    realtime.onResync(onReload);
    realtime.on(onReload);
    realtime.connect(board.projectId);
    return () => {
      realtime.offResync(onReload);
      realtime.off(onReload);
    };
  }, [board?.projectId]);

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