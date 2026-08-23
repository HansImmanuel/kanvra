"use client";

import { use, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { get } from "@/lib/api";
import Board from "@/components/board/Board";
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
  const [board, setBoard] = useState<BoardDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const proj = await get<Project>(`/api/v1/projects/${pid}`);
      setProject(proj);
      const boards = await get<BoardRef[]>(`/api/v1/projects/${pid}/boards`);
      if (!boards || boards.length === 0) {
        setBoard(null);
        return;
      }
      const detail = await get<BoardDetail>(`/api/v1/boards/${boards[0].id}`);
      setBoard(detail);
    } catch (err) {
      if (err instanceof Error && "status" in err && (err as { status: number }).status === 401) {
        router.replace("/login");
        return;
      }
      setError(err instanceof Error ? err.message : "Failed to load board");
    }
  }, [pid, router]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <main className="min-h-full p-6">
      <div className="mb-4">
        <a href="/dashboard" className="text-sm text-slate-500">
          ← Dashboard
        </a>
        <h1 className="text-2xl font-bold text-slate-800">{project?.name ?? "Board"}</h1>
        {board && <p className="text-sm text-slate-500">{board.name}</p>}
      </div>
      {error && <p className="rounded bg-red-100 border border-red-300 p-3 text-sm">{error}</p>}
      {board ? (
        <Board
          board={board}
          onReload={() => void load()}
        />
      ) : !error ? (
        <p className="text-slate-500">Loading board…</p>
      ) : null}
    </main>
  );
}