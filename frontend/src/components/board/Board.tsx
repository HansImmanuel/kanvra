"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useMemo, useState } from "react";
import { post, ApiError } from "@/lib/api";
import { realtime, noteLocalEventId } from "@/lib/websocket";
import TaskCard from "@/components/task/TaskCard";
import { Button, Input, Tooltip } from "@/components/ui";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Pencil,
  Trash2,
  X
} from "lucide-react";
import { createColumn, deleteColumn, renameColumn, reorderColumns } from "@/lib/actions";
import type { BoardDetail, ColumnDetail, TaskCard as TaskCardType, TaskResponse } from "@/types";

// Loaded on demand: pulls in CommentsThread + pickers, opened per card click.
const TaskDetailModal = dynamic(() => import("@/components/task/TaskDetailModal"));

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

/**
 * Local add-card composer: owns its input state so keystrokes re-render only
 * this composer, not every column and card on the board (phase 10 perf fix).
 */
function AddCardForm({
  columnLabel,
  disabled,
  onCreate
}: {
  columnLabel: string;
  disabled: boolean;
  onCreate: (title: string) => Promise<void>;
}) {
  const [title, setTitle] = useState("");
  const submit = async () => {
    const trimmed = title.trim();
    if (!trimmed) return;
    await onCreate(trimmed);
    setTitle("");
  };
  return (
    <div className="mt-1 border-t border-edge pt-2">
      <Input
        fieldSize="sm"
        className="w-full"
        aria-label={`Add a card to ${columnLabel}`}
        placeholder="Add a card"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") void submit();
        }}
      />
      <Button
        variant="primary"
        size="compact"
        className="mt-1 w-full"
        disabled={disabled || !title.trim()}
        onClick={() => void submit()}
      >
        {disabled && (
          <Loader2
            size={13}
            className="mr-1 inline animate-spin motion-reduce:animate-none"
            aria-hidden="true"
          />
        )}
        + Add
      </Button>
    </div>
  );
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

  const createTask = async (columnId: number, rawTitle: string) => {
    const title = rawTitle.trim();
    if (!title) return;
    setBusy(true);
    try {
      const created = await post<TaskResponse>(`/api/v1/columns/${columnId}/tasks`, { title });
      noteLocalEventId(created.eventId);
      onReload();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        onReload(); // server state changed; re-sync before the next action
      } else if (err instanceof ApiError) {
        // Creation failures were previously silent; surface them like move errors.
        setFlash(err.message || "Failed to create task");
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
  // a remote (non-local-echo) event arrives. Re-subscribes when onReload changes
  // so the listener never holds a stale active-board closure (phase 10 fix).
  useEffect(() => {
    if (!board?.projectId) return;
    realtime.onResync(onReload);
    realtime.on(onReload);
    realtime.connect(board.projectId);
    return () => {
      realtime.offResync(onReload);
      realtime.off(onReload);
    };
  }, [board?.projectId, onReload]);

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

  // Stable handler so memoized TaskCards skip re-renders on board updates.
  const selectTask = useCallback((task: TaskCardType) => setSelectedTaskId(task.id), []);

  // Card preview for the detail modal's read-only assignee/labels display.
  const findCard = (taskId: number): TaskCardType | undefined => {
    for (const column of board.columns) {
      const found = column.tasks.find((t) => t.id === taskId);
      if (found) return found;
    }
    return undefined;
  };

  return (
    <div className="flex scroll-smooth gap-4 overflow-x-auto p-4 pb-6 motion-reduce:scroll-auto" data-testid="board">
      {flash && (
        <div
          role="status"
          className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-panel bg-primary px-4 py-2.5 text-sm text-white shadow-toast animate-kv-fade-in motion-reduce:animate-none"
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
          <div key={column.id} className="flex w-72 min-w-72 shrink-0 flex-col gap-2 rounded-panel border border-edge bg-elevated/60 p-2">
            <div className="rounded-panel border border-transparent px-1 py-1 transition-colors hover:border-edge">
              {isRenaming ? (
                <form
                  className="flex items-center gap-1"
                  onSubmit={(e) => {
                    e.preventDefault();
                    void submitRenameColumn(column.id);
                  }}
                >
                  <Input
                    fieldSize="xs"
                    className="w-full"
                    aria-label={`Rename column ${column.name}`}
                    autoFocus
                    value={renameValue}
                    onChange={(e) => setRenameValue(e.target.value)}
                  />
                  <button type="submit" aria-label="Save column name" disabled={busy} className="text-xs">
                    <Check size={14} aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    aria-label={`Cancel renaming ${column.name}`}
                    onClick={() => setRenamingColumnId(null)}
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </form>
              ) : (
                <div className="flex items-center justify-between gap-1">
                  <div className="flex min-w-0 items-center gap-1.5">
                    <h2 className="truncate text-sm font-semibold text-heading">{column.name}</h2>
                    <span
                      className="shrink-0 rounded-full bg-primary/10 px-1.5 py-px text-[11px] font-medium text-body"
                      title={`${column.tasks.length} tasks`}
                    >
                      {column.tasks.length}
                    </span>
                  </div>
                  <span className="flex shrink-0 items-center gap-0.5 text-muted">
                    <Tooltip label="Move column left">
                      <button
                        type="button"
                        disabled={busy || idx === 0}
                        aria-label={`move column ${column.name} left`}
                        className="flex size-7 items-center justify-center rounded-panel text-muted transition-colors hover:bg-primary/10 hover:text-body disabled:opacity-30 motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-accent"
                        onClick={() => void shiftColumn(column.id, -1)}
                      >
                        <ChevronLeft size={14} aria-hidden="true" />
                      </button>
                    </Tooltip>
                    <Tooltip label="Move column right">
                      <button
                        type="button"
                        disabled={busy || idx === displayedColumns.length - 1}
                        aria-label={`move column ${column.name} right`}
                        className="flex size-7 items-center justify-center rounded-panel text-muted transition-colors hover:bg-primary/10 hover:text-body disabled:opacity-30 motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-accent"
                        onClick={() => void shiftColumn(column.id, 1)}
                      >
                        <ChevronRight size={14} aria-hidden="true" />
                      </button>
                    </Tooltip>
                    <Tooltip label="Rename column">
                      <button
                        type="button"
                        aria-label={`rename column ${column.name}`}
                        className="flex size-7 items-center justify-center rounded-panel text-muted transition-colors hover:bg-primary/10 hover:text-body disabled:opacity-30 motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-accent"
                        onClick={() => {
                          setRenamingColumnId(column.id);
                          setRenameValue(column.name);
                        }}
                      >
                        <Pencil size={13} aria-hidden="true" />
                      </button>
                    </Tooltip>
                    <Tooltip label="Delete column">
                      <button
                        type="button"
                        disabled={busy}
                        aria-label={`delete column ${column.name}`}
                        className="flex size-7 items-center justify-center rounded-panel text-muted transition-colors hover:bg-red-100 hover:text-red-700 disabled:opacity-30 motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-accent"
                        onClick={() => {
                          setDeletingColumnId(column.id);
                          setDeleteTargetId("");
                        }}
                      >
                        <Trash2 size={13} aria-hidden="true" />
                      </button>
                    </Tooltip>
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
                  <Button
                    variant="danger"
                    size="sm"
                    disabled={busy || (column.tasks.length > 0 && deleteTargetId === "")}
                    onClick={() => void submitDeleteColumn()}
                  >
                    Confirm delete
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setDeletingColumnId(null)}>
                    Cancel
                  </Button>
                </div>
              </div>
            )}

            {column.tasks.map((task) => (
              <div key={task.id} className="flex">
                {prevCol && (
                  <button
                    className="flex size-8 shrink-0 items-center justify-center self-center rounded-panel text-muted transition-colors hover:bg-primary/10 hover:text-body disabled:opacity-30 motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-accent"
                    disabled={busy}
                    onClick={() => moveTask(task, prevCol.id, 0)}
                    aria-label={`move ${task.title} to ${prevCol.name}`}
                  >
                    <ArrowLeft size={14} aria-hidden="true" />
                  </button>
                )}
                <TaskCard
                  task={{ ...task, version: versionOverrides[task.id] ?? task.version }}
                  onSelect={selectTask}
                  moving={pendingMove?.taskId === task.id}
                />
                {nextCol && (
                  <button
                    className="flex size-8 shrink-0 items-center justify-center self-center rounded-panel text-muted transition-colors hover:bg-primary/10 hover:text-body disabled:opacity-30 motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-accent"
                    disabled={busy}
                    onClick={() => moveTask(task, nextCol.id, appendPos(nextCol.id))}
                    aria-label={`move ${task.title} to ${nextCol.name}`}
                  >
                    <ArrowRight size={14} aria-hidden="true" />
                  </button>
                )}
              </div>
            ))}

            {column.tasks.length === 0 && (
              <p className="rounded-panel border border-dashed border-edge px-3 py-4 text-center text-xs text-faint">
                No tasks yet
              </p>
            )}

            <AddCardForm
              columnLabel={column.name}
              disabled={busy}
              onCreate={(title) => createTask(column.id, title)}
            />
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
            <Input
              fieldSize="sm"
              className="w-full"
              aria-label="New column name"
              autoFocus
              placeholder="New column name"
              value={newColumnName}
              onChange={(e) => setNewColumnName(e.target.value)}
            />
            <div className="flex gap-2">
              <Button
                type="submit"
                variant="primary"
                size="compact"
                disabled={busy || !newColumnName.trim()}
              >
                Create column
              </Button>
              <Button
                variant="outline"
                size="compact"
                onClick={() => {
                  setAddingColumn(false);
                  setNewColumnName("");
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        ) : (
          <Button variant="dashed" onClick={() => setAddingColumn(true)}>
            + Add column
          </Button>
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