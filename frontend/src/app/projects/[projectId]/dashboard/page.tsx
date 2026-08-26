"use client";

import { use, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { get } from "@/lib/api";
import { listMembers } from "@/lib/actions";
import type { BoardRef, Member, Project } from "@/types";

/**
 * Project Dashboard (project overview, docs/PRD.md §7). A compact summary of the
 * project — details, members, and boards — with a CTA into the board. All data
 * comes from existing membership-gated APIs; no new backend surface.
 */
export default function ProjectDashboardPage({
  params
}: {
  params: Promise<{ projectId: string }>;
}) {
  const { projectId } = use(params);
  const pid = Number(projectId);

  const [project, setProject] = useState<Project | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [boards, setBoards] = useState<BoardRef[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const [proj, memberPage, boardList] = await Promise.all([
        get<Project>(`/api/v1/projects/${pid}`),
        listMembers(pid, 0, 100),
        get<BoardRef[]>(`/api/v1/projects/${pid}/boards`)
      ]);
      setProject(proj);
      setMembers(memberPage.content);
      setBoards(boardList);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load project");
    } finally {
      setLoading(false);
    }
  }, [pid]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <p className="text-slate-500">Loading project…</p>;
  }

  return (
    <div className="max-w-3xl space-y-6">
      {error && <p className="rounded bg-red-100 border border-red-300 p-3 text-sm">{error}</p>}

      {project && (
        <section className="rounded-lg border border-slate-300 bg-white p-5">
          <h2 className="text-lg font-semibold text-slate-800">{project.name}</h2>
          <p className="mt-1 text-sm text-slate-500">
            {project.description || "No description."}
          </p>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-slate-400">Status</dt>
              <dd className="font-medium text-slate-700">{project.status}</dd>
            </div>
            <div>
              <dt className="text-slate-400">Boards</dt>
              <dd className="font-medium text-slate-700">{boards.length}</dd>
            </div>
            <div>
              <dt className="text-slate-400">Members</dt>
              <dd className="font-medium text-slate-700">{members.length}</dd>
            </div>
          </dl>
          <Link
            href={`/projects/${pid}/board`}
            className="mt-4 inline-block rounded bg-slate-700 px-4 py-2 text-sm text-white hover:bg-slate-600"
          >
            Open board →
          </Link>
        </section>
      )}

      <section className="rounded-lg border border-slate-300 bg-white p-5">
        <h3 className="mb-2 text-sm font-semibold text-slate-700">Members</h3>
        {members.length === 0 ? (
          <p className="text-sm text-slate-400">No members yet.</p>
        ) : (
          <ul className="grid gap-1 sm:grid-cols-2">
            {members.map((m) => (
              <li key={m.id} className="flex items-center justify-between rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm">
                <span className="text-slate-700">{m.name}</span>
                <span className="rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-600">{m.role}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="rounded-lg border border-slate-300 bg-white p-5">
        <h3 className="mb-2 text-sm font-semibold text-slate-700">Boards</h3>
        {boards.length === 0 ? (
          <p className="text-sm text-slate-400">No boards yet.</p>
        ) : (
          <ul className="grid gap-1 sm:grid-cols-2">
            {boards.map((b) => (
              <li key={b.id}>
                <Link
                  href={`/projects/${pid}/board`}
                  className="flex items-center justify-between rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 hover:border-slate-400"
                >
                  {b.name}
                  <span aria-hidden className="text-slate-400">→</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}