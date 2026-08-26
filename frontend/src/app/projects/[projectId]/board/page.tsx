"use client";

import { use, useCallback, useEffect, useRef, useState } from "react";
import { get } from "@/lib/api";
import Board from "@/components/board/Board";
import BoardSwitcher from "@/components/board/BoardSwitcher";
import ActivityPanel from "@/components/activity/ActivityPanel";
import { Modal } from "@/components/ui";
import { createBoard as createBoardAction } from "@/lib/actions";
import type { BoardDetail, BoardRef } from "@/types";

/**
 * Project Board page (default landing, docs/PRD.md §7). Renders the Kanban
 * board as the primary focus. The activity feed is now a centered modal
 * (blurred/dimmed backdrop) opened from the toolbar instead of permanently
 * occupying space underneath the board.
 */
export default function ProjectBoardPage({
  params
}: {
  params: Promise<{ projectId: string }>;
}) {
  const { projectId } = use(params);
  const pid = Number(projectId);

  const [boards, setBoards] = useState<BoardRef[]>([]);
  const [activeBoardId, setActiveBoardId] = useState<number | null>(null);
  const [board, setBoard] = useState<BoardDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [boardBusy, setBoardBusy] = useState(false);
  const [showActivity, setShowActivity] = useState(false);
  // Guards the activeBoardId effect so the mount load doesn't double-fetch.
  const skipActiveEffect = useRef(true);

  const fetchBoardDetail = useCallback(
    async (boardId: number) => {
      setBoard(await get<BoardDetail>(`/api/v1/boards/${boardId}`));
    },
    []
  );

  const selectBoard = useCallback(
    async (boardId: number) => {
      if (boardId === activeBoardId) return;
      setActiveBoardId(boardId);
      setError(null);
      try {
        await fetchBoardDetail(boardId);
      } catch {
        setError("Failed to load board");
      }
    },
    [activeBoardId, fetchBoardDetail]
  );

  const reloadActiveBoard = useCallback(async () => {
    if (activeBoardId == null) return;
    try {
      await fetchBoardDetail(activeBoardId);
    } catch {
      /* transient; realtime resync will retry */
    }
  }, [activeBoardId, fetchBoardDetail]);

  const load = useCallback(async () => {
    try {
      const list = await get<BoardRef[]>(`/api/v1/projects/${pid}/boards`);
      setBoards(list);
      if (!list || list.length === 0) {
        setActiveBoardId(null);
        setBoard(null);
        return;
      }
      const target = list.find((b) => b.id === activeBoardId)?.id ?? list[0].id;
      if (target !== activeBoardId) setActiveBoardId(target);
      await fetchBoardDetail(target);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load board");
    }
    // Runs once on mount; user actions drive later refreshes.
  }, [pid, fetchBoardDetail]);

  useEffect(() => {
    void load();
  }, [load]);

  // When the active board changes (user selection), load its detail.
  useEffect(() => {
    if (skipActiveEffect.current) {
      skipActiveEffect.current = false;
      return;
    }
    if (activeBoardId == null) return;
    void fetchBoardDetail(activeBoardId).catch(() =>
      setError("Failed to load board")
    );
  }, [activeBoardId, fetchBoardDetail]);

  const handleCreateBoard = async (name: string) => {
    setBoardBusy(true);
    setError(null);
    try {
      const created = await createBoardAction(pid, name);
      setBoards((prev) => [...prev, created]);
      setActiveBoardId(created.id);
      await fetchBoardDetail(created.id);
    } catch {
      setError("Failed to create board");
    } finally {
      setBoardBusy(false);
    }
  };

  return (
    <div>
      {boards.length > 0 && (
        <div className="mb-4">
          <BoardSwitcher
            boards={boards}
            activeId={activeBoardId}
            onSelect={(id) => void selectBoard(id)}
            onCreate={(name) => void handleCreateBoard(name)}
            busy={boardBusy}
          />
        </div>
      )}

      <div className="mb-4 flex items-center justify-between gap-4">
        <h2 className="text-lg font-semibold text-slate-700">Kanban Board</h2>
        <button
          type="button"
          className="rounded border border-slate-300 px-3 py-1 text-sm text-slate-600 hover:border-slate-500"
          onClick={() => setShowActivity(true)}
        >
          Activity
        </button>
      </div>

      {error && <p className="rounded bg-red-100 border border-red-300 p-3 text-sm">{error}</p>}
      {board ? (
        <Board board={board} onReload={() => void reloadActiveBoard()} />
      ) : !error ? (
        <p className="text-slate-500">Loading board…</p>
      ) : null}

      {showActivity && (
        <Modal
          open
          onClose={() => setShowActivity(false)}
          title="Activity"
          widthClass="max-w-2xl"
          backdropBlur
        >
          <ActivityPanel projectId={pid} />
        </Modal>
      )}
    </div>
  );
}