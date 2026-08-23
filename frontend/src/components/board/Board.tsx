"use client";

import { useEffect, useMemo, useState } from "react";
import { post, ApiError } from "@/lib/api";
import { realtime, noteLocalEventId } from "@/lib/websocket";
import TaskCard from "@/components/task/TaskCard";
import TaskDetailModal from "@/components/task/TaskDetailModal";
import { createColumn, deleteColumn, renameColumn, reorderColumns } from "@/lib/actions";
import type { BoardDetail, ColumnDetail, TaskCard as TaskCardType, TaskResponse } from "@/types";

interface PendingMove {
  taskId: number;
  toColumnId: number;
  position: number;
}

/**
 * Pure optimistic transform (TECH_DOC §14 "Board state" step 1): lift a task
 * out of its current column and reinsert it at the target position.
 */
export function applyMove(
  columns: ColumnDetail[],
  taskId: number,
  toColumnId: number,
  position: number
): ColumnDetail[] {
  const task = columns.flatMap((c) => c.tasks).find((t) => t.id === taskId);
  if (!task) return columns;
  return columns.map((c) => {
    if (c.id === toColumnId) {
      const without = c.tasks.filter((t) => t.id !== taskId);
      const next = [...without];
      next.splice(Math.max(0, Math.min(position, next.length)), 0, task);
      return { ...c, tasks: next };
    }
    return c.tasks.some((t) => t.id === taskId)
      ? { ...c, tasks: c.tasks.filter((t) => t.id !== taskId) }
      : c;
  });
}

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
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);

  // Optimistic move state (TECH_DOC §14): local layout override while a move
  // is in flight, plus version corrections learned from 409 conflicts.
  const [pendingMove, setPendingMove] = useState<PendingMove | null>(null);
  const [versionOverrides, setVersionOverrides] = useState<Record<number, number>>({});
  const [flash, setFlash] = useState<string | null>(null);

  // Column management (SPEC §6).
  const [addingColumn, setAddingColumn] = useState(false);
  const [newColumnName, setNewColumnName] = useState("");
  const [renamingColumnId, setRenamingColumnId] = useState<number | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [deletingColumnId, setDeletingColumnId] = useState<number | null>(null);
  const [deleteTargetId, setDeleteTargetId] = useState("");

  // Auto-dismiss the toast-style flash.
  useEffect(() => {
    if (!flash) return;
    const t = setTimeout(() => setFlash(null), 4000);
    return () => clearTimeout(t);
  }, [flash]);

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
    // TECH_DOC §14 "Board state": optimistic update first, then reconcile with
    // the server response/event.
    setPendingMove({ taskId: task.id, toColumnId: targetColumnId, position });
    setBusy(true);
    try {
      const moved = await post<TaskResponse>(`/api/v1/tasks/${task.id}/move`, {
        targetColumnId,
        position,
        version: versionOverrides[task.id] ?? task.version
      });
      noteLocalEventId(moved.eventId);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // TASK_VERSION_CONFLICT — roll back and adopt the server's current
        // version from the conflict body so a retry can succeed (SPEC §7.2).
        const serverState = err.data?.currentState;
        if (serverState) {
          setVersionOverrides((v) => ({ ...v, [serverState.id]: serverState.version }));
        }
        setFlash("Task was changed by someone else — board re-synced, please retry.");
      } else if (err instanceof ApiError) {
        setFlash(err.message || "Move failed");
      }
    } finally {
      // Settle the override; onReload replaces local state with server truth.
      setPendingMove(null);
      onReload();
      setBusy(false);
    }
  };

  // --- Column management (SPEC §6) ---------------------------------------
  const submitNewColumn = async () => {
    const name = newColumnName.trim();
    if (!name) return;
    setBusy(true);
    try {
      await createColumn(board.id, name);
      setNewColumnName("");
      setAddingColumn(false);
      onReload();
    } catch (err) {
      setFlash(err instanceof ApiError ? err.message : "Failed to create column");
    } finally {
      setBusy(false);
    }
  };

  const submitRenameColumn = async (columnId: number) => {
    const name = renameValue.trim();
    if (!name) {
      setRenamingColumnId(null);
      return;
    }
    setBusy(true);
    try {
      await renameColumn(columnId, name);
      setRenamingColumnId(null);
      onReload();
    } catch (err) {
      setFlash(err instanceof ApiError ? err.message : "Failed to rename column");
    } finally {
      setBusy(false);
    }
  };

  const submitDeleteColumn = async () => {
    if (deletingColumnId == null) return;
    const column = board.columns.find((c) => c.id === deletingColumnId);
    const hasTasks = (column?.tasks.length ?? 0) > 0;
    if (hasTasks && deleteTargetId === "") {
      setFlash("Pick a column to move the tasks into first");
      return;
    }
    setBusy(true);
    try {
      await deleteColumn(deletingColumnId, hasTasks ? Number(deleteTargetId) : undefined);
      setDeletingColumnId(null);
      setDeleteTargetId("");
      onReload();
    } catch (err) {
      setFlash(err instanceof ApiError ? err.message : "Failed to delete column");
    } finally {
      setBusy(false);
    }
  };

  const shiftColumn = async (columnId: number, dir: -1 | 1) => {
    const ids = board.columns.map((c) => c.id);
    const i = ids.indexOf(columnId);
    const j = i + dir;
    if (i < 0 || j < 0 || j >= ids.length) return;
    [ids[i], ids[j]] = [ids[j], ids[i]];
    setBusy(true);
    try {
      await reorderColumns(board.id, ids);
      onReload();
    } catch (err) {
      setFlash(err instanceof ApiError ? err.message : "Failed to reorder columns");
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

  // What the user sees right now: server truth overlaid with any in-flight move.
  const displayedColumns = useMemo(
    () =>
      pendingMove
        ? applyMove(board.columns, pendingMove.taskId, pendingMove.toColumnId, pendingMove.position)
        : board.columns,
    [board.columns, pendingMove]
  );

  const columnIndex = (id: number) => displayedColumns.findIndex((c) => c.id === id);
  const appendPos = (columnId: number) =>
    displayedColumns.find((c) => c.id === columnId)?.tasks.length ?? 0;

  // Card preview for the detail modal's read-only assignee/labels display.
  const findCard = (taskId: number): TaskCardType | undefined => {
    for (const column of board.columns) {
      const found = column.tasks.find((t) => t.id === taskId);
      if (found) return found;
    }
    return undefined;
  };

  return (
    <div className="flex gap-4 overflow-x-auto p-4" data-testid="board">
      {flash && (
        <div
          role="status"
          className="fixed bottom-4 left-1/2 z-50 -translate-x-1/2 rounded bg-slate-800 px-4 py-2 text-sm text-white shadow-lg"
        >
          {flash}
        </div>
      )}
      {displayedColumns.map((column) => {
        const idx = columnIndex(column.id);
        const nextCol = displayedColumns[idx + 1];
        const prevCol = displayedColumns[idx - 1];
        const isDeleting = deletingColumnId === column.id;
        const isRenaming = renamingColumnId === column.id;
        return (
          <div key={column.id} className="flex flex-col w-72 min-w-72 gap-2">
            <div className="rounded bg-slate-200 px-3 py-2">
              {isRenaming ? (
                <form
                  className="flex items-center gap-1"
                  onSubmit={(e) => {
                    e.preventDefault();
                    void submitRenameColumn(column.id);
                  }}
                >
                  <input
                    aria-label={`Rename column ${column.name}`}
                    autoFocus
                    className="w-full rounded border border-slate-300 px-1 py-0.5 text-sm"
                    value={renameValue}
                    onChange={(e) => setRenameValue(e.target.value)}
                  />
                  <button type="submit" aria-label="Save column name" disabled={busy} className="text-xs">
                    ✓
                  </button>
                  <button
                    type="button"
                    aria-label={`Cancel renaming ${column.name}`}
                    onClick={() => setRenamingColumnId(null)}
                  >
                    ✕
                  </button>
                </form>
              ) : (
                <div className="flex items-center justify-between gap-1">
                  <h2 className="font-semibold text-slate-700">{column.name}</h2>
                  <span className="flex gap-0.5 text-slate-500">
                    <button
                      type="button"
                      disabled={busy || idx === 0}
                      aria-label={`move column ${column.name} left`}
                      className="px-1 hover:text-slate-800 disabled:opacity-30"
                      onClick={() => void shiftColumn(column.id, -1)}
                    >
                      ‹
                    </button>
                    <button
                      type="button"
                      disabled={busy || idx === displayedColumns.length - 1}
                      aria-label={`move column ${column.name} right`}
                      className="px-1 hover:text-slate-800 disabled:opacity-30"
                      onClick={() => void shiftColumn(column.id, 1)}
                    >
                      ›
                    </button>
                    <button
                      type="button"
                      aria-label={`rename column ${column.name}`}
                      className="px-1 hover:text-slate-800"
                      onClick={() => {
                        setRenamingColumnId(column.id);
                        setRenameValue(column.name);
                      }}
                    >
                      ✎
                    </button>
                    <button
                      type="button"
                      disabled={busy}
                      aria-label={`delete column ${column.name}`}
                      className="px-1 hover:text-red-600 disabled:opacity-30"
                      onClick={() => {
                        setDeletingColumnId(column.id);
                        setDeleteTargetId("");
                      }}
                    >
                      🗑
                    </button>
                  </span>
                </div>
              )}
            </div>

            {isDeleting && (
              <div className="rounded bg-red-50 p-2 text-xs text-slate-700">
                {column.tasks.length === 0 ? (
                  <p>Delete this empty column?</p>
                ) : (
                  <label className="mb-1 block">
                    Move {column.tasks.length} task(s) to:
                    <select
                      aria-label={`Target column for ${column.name}`}
                      className="mt-1 w-full rounded border border-slate-300 px-1 py-0.5"
                      value={deleteTargetId}
                      onChange={(e) => setDeleteTargetId(e.target.value)}
                    >
                      <option value="">Select…</option>
                      {displayedColumns
                        .filter((c) => c.id !== column.id)
                        .map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.name}
                          </option>
                        ))}
                    </select>
                  </label>
                )}
                <div className="mt-1 flex gap-2">
                  <button
                    type="button"
                    disabled={busy || (column.tasks.length > 0 && deleteTargetId === "")}
                    className="rounded bg-red-600 px-2 py-0.5 text-white disabled:opacity-40"
                    onClick={() => void submitDeleteColumn()}
                  >
                    Confirm delete
                  </button>
                  <button
                    type="button"
                    className="rounded border border-slate-300 px-2 py-0.5"
                    onClick={() => setDeletingColumnId(null)}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}

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
                <TaskCard
                  task={{ ...task, version: versionOverrides[task.id] ?? task.version }}
                  onSelect={(t) => setSelectedTaskId(t.id)}
                />
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

      {/* New column (SPEC §6) */}
      <div className="flex w-56 min-w-56 flex-col justify-start">
        {addingColumn ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void submitNewColumn();
            }}
            className="space-y-2 rounded bg-slate-100 p-3"
          >
            <input
              aria-label="New column name"
              autoFocus
              placeholder="New column name"
              className="w-full rounded border border-slate-300 px-2 py-1 text-sm"
              value={newColumnName}
              onChange={(e) => setNewColumnName(e.target.value)}
            />
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={busy || !newColumnName.trim()}
                className="rounded bg-slate-700 px-2 py-1 text-sm text-white disabled:opacity-50"
              >
                Create column
              </button>
              <button
                type="button"
                className="rounded border border-slate-300 px-2 py-1 text-sm"
                onClick={() => {
                  setAddingColumn(false);
                  setNewColumnName("");
                }}
              >
                Cancel
              </button>
            </div>
          </form>
        ) : (
          <button
            type="button"
            className="rounded border border-dashed border-slate-400 px-3 py-2 text-sm text-slate-500 hover:border-slate-600 hover:text-slate-700"
            onClick={() => setAddingColumn(true)}
          >
            + Add column
          </button>
        )}
      </div>

      {selectedTaskId != null && (
        <TaskDetailModal
          projectId={board.projectId}
          taskId={selectedTaskId}
          card={findCard(selectedTaskId)}
          onClose={() => setSelectedTaskId(null)}
          onChanged={() => onReload()}
        />
      )}
    </div>
  );
}