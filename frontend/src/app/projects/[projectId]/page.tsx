"use client";

import { use, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { get } from "@/lib/api";
import Board from "@/components/board/Board";
import BoardSwitcher from "@/components/board/BoardSwitcher";
import ProjectSettingsPanel from "@/components/project/ProjectSettingsPanel";
import NotificationBell from "@/components/notification/NotificationBell";
import ActivityPanel from "@/components/activity/ActivityPanel";
import { createBoard as createBoardAction } from "@/lib/actions";
import type { BoardDetail, BoardRef, Project } from "@/types";

export default function ProjectPage({
  params
}: {
  params: Promise<{ projectId: string }>;
}) {
  const router = useRouter();
  const { projectId } = use(params);
  const pid = Number(projectId);

  const [project, setProject] = useState<Project | null>(null);
  const [boards, setBoards] = useState<BoardRef[]>([]);
  const [activeBoardId, setActiveBoardId] = useState<number | null>(null);
  const [board, setBoard] = useState<BoardDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showSettings, setShowSettings] = useState(false);
  const [boardBusy, setBoardBusy] = useState(false);
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
      const proj = await get<Project>(`/api/v1/projects/${pid}`);
      setProject(proj);
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
      if (err instanceof Error && "status" in err && (err as { status: number }).status === 401) {
        router.replace("/login");
        return;
      }
      setError(err instanceof Error ? err.message : "Failed to load board");
    }
    // Runs once on mount; user actions drive later refreshes.
  }, [pid, router]);

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
    <main className="min-h-full p-6">
      <div className="mb-4">
        <a href="/dashboard" className="text-sm text-slate-500">
          ← Dashboard
        </a>
        <div className="flex items-center justify-between gap-4">
          <h1 className="text-2xl font-bold text-slate-800">{project?.name ?? "Board"}</h1>
          <div className="flex items-center gap-2">
            {project && (
              <button
                type="button"
                className="rounded border border-slate-300 px-3 py-1 text-sm hover:border-slate-500"
                onClick={() => setShowSettings(true)}
              >
                ⚙ Settings
              </button>
            )}
            <NotificationBell />
          </div>
        </div>
        {boards.length > 0 && (
          <div className="mt-3">
            <BoardSwitcher
              boards={boards}
              activeId={activeBoardId}
              onSelect={(id) => void selectBoard(id)}
              onCreate={(name) => void handleCreateBoard(name)}
              busy={boardBusy}
            />
          </div>
        )}
      </div>
      {error && <p className="rounded bg-red-100 border border-red-300 p-3 text-sm">{error}</p>}
      {board ? (
        <Board
          board={board}
          onReload={() => void reloadActiveBoard()}
        />
      ) : !error ? (
        <p className="text-slate-500">Loading board…</p>
      ) : null}
      {showSettings && project && (
        <ProjectSettingsPanel project={project} onClose={() => setShowSettings(false)} />
      )}
      {project && (
        <div className="mt-6 max-w-3xl">
          <ActivityPanel projectId={project.id} />
        </div>
      )}
    </main>
  );
}