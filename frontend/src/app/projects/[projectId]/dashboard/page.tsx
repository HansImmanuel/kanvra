"use client";

import { use, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  SquareKanban,
  TrendingUp,
  Users
} from "lucide-react";
import { get } from "@/lib/api";
import { listMembers } from "@/lib/actions";
import { Avatar, Card, ErrorState, Skeleton } from "@/components/ui";
import type { BoardRef, Member, Project } from "@/types";

/**
 * Project Dashboard (project overview, docs/PRD.md §7): what the project is,
 * its current context, and where to go next. Only existing data is shown —
 * the two counts are real, no derived or invented metrics. All data comes
 * from existing membership-gated APIs; no new backend surface.
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
    return (
      <div className="max-w-3xl space-y-4" aria-busy="true" aria-label="Loading project">
        {/* Summary: title + status chip, description, meta, CTAs */}
        <Card pad="lg">
          <div className="flex items-start justify-between gap-3">
            <Skeleton className="h-5 w-48" />
            <Skeleton className="h-5 w-16 rounded-full" />
          </div>
          <Skeleton className="mt-3 h-3 w-full" />
          <Skeleton className="mt-1.5 h-3 w-2/3" />
          <div className="mt-3 flex gap-4">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-3 w-20" />
          </div>
          <div className="mt-4 flex gap-2 border-t border-edge pt-4">
            <Skeleton className="h-9 w-28 rounded-panel" />
            <Skeleton className="h-9 w-32 rounded-panel" />
          </div>
        </Card>

        {/* Members: avatar + name rows */}
        <Card>
          <Skeleton className="h-3.5 w-20" />
          <div className="mt-3 grid gap-1.5 sm:grid-cols-2">
            {[0, 1, 2, 3].map((i) => (
              <div
                key={i}
                className="flex items-center gap-2.5 rounded-panel border border-edge bg-elevated px-3 py-2"
              >
                <Skeleton className="size-7 rounded-full" />
                <Skeleton className="h-3 flex-1" />
              </div>
            ))}
          </div>
        </Card>

        {/* Boards: link rows */}
        <Card>
          <Skeleton className="h-3.5 w-16" />
          <div className="mt-3 space-y-1.5">
            {[0, 1].map((i) => (
              <div
                key={i}
                className="flex items-center gap-2.5 rounded-panel border border-edge bg-elevated px-3 py-2"
              >
                <Skeleton className="size-5 rounded-panel" />
                <Skeleton className="h-3 flex-1" />
              </div>
            ))}
          </div>
        </Card>
      </div>
    );
  }

  const isActive = project?.status === "ACTIVE";
  // Owners first; stable for same-role members.
  const sortedMembers = [...members].sort(
    (a, b) => (a.role === "OWNER" ? 0 : 1) - (b.role === "OWNER" ? 0 : 1)
  );

  const ctaPrimary =
    "inline-flex items-center gap-1.5 rounded-panel bg-primary px-3.5 py-2 text-sm " +
    "font-medium text-white transition-colors hover:bg-primary-hover " +
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent motion-reduce:transition-none";
  const ctaSecondary =
    "inline-flex items-center gap-1.5 rounded-panel border border-edge-strong bg-surface " +
    "px-3.5 py-2 text-sm text-dim transition-colors hover:border-primary-hover hover:text-body " +
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent motion-reduce:transition-none";

  return (
    <div className="max-w-3xl space-y-4">
      {error && <ErrorState message={error} onRetry={() => void load()} />}

      {project && (
        <Card pad="lg">
          <div className="flex items-start justify-between gap-3">
            <h2 className="text-lg font-semibold text-heading">{project.name}</h2>
            <span
              className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide ${
                isActive ? "bg-positive/10 text-positive" : "bg-caution/10 text-caution"
              }`}
            >
              {project.status}
            </span>
          </div>

          <p className={`mt-1 text-sm ${project.description ? "text-dim" : "text-faint"}`}>
            {project.description || "No description yet."}
          </p>

          <div className="mt-3 flex items-center gap-4 text-xs text-muted">
            <span className="inline-flex items-center gap-1.5">
              <Users size={13} aria-hidden="true" />
              {members.length} members
            </span>
            <span className="inline-flex items-center gap-1.5">
              <SquareKanban size={13} aria-hidden="true" />
              {boards.length} boards
            </span>
          </div>

          <div className="mt-4 flex flex-wrap gap-2 border-t border-edge pt-4">
            <Link href={`/projects/${pid}/board`} className={ctaPrimary}>
              Open board
              <ArrowRight size={14} aria-hidden="true" />
            </Link>
            <Link href={`/projects/${pid}/analytics`} className={ctaSecondary}>
              View analytics
              <TrendingUp size={14} aria-hidden="true" />
            </Link>
          </div>
        </Card>
      )}

      <Card>
        <h3 className="text-sm font-semibold text-heading">Members</h3>
        {members.length === 0 ? (
          <div className="mt-3 flex flex-col items-center gap-1.5 rounded-panel border border-dashed border-edge px-4 py-6 text-center">
            <span
              aria-hidden="true"
              className="flex size-9 items-center justify-center rounded-full bg-primary/10 text-primary"
            >
              <Users size={16} />
            </span>
            <p className="text-sm font-medium text-heading">No members yet</p>
            <p className="text-xs text-faint">Members added by the project owner will appear here.</p>
          </div>
        ) : (
          <ul className="mt-3 grid gap-1.5 sm:grid-cols-2">
            {sortedMembers.map((m) => (
              <li
                key={m.id}
                className="flex items-center gap-2.5 rounded-panel border border-edge bg-elevated px-3 py-2"
              >
                <Avatar name={m.name} size={28} />
                <span className="min-w-0 flex-1 truncate text-sm text-body">{m.name}</span>
                <span
                  className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] uppercase tracking-wide ${
                    m.role === "OWNER"
                      ? "bg-primary/10 font-semibold text-primary"
                      : "bg-surface font-medium text-muted"
                  }`}
                >
                  {m.role}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <h3 className="text-sm font-semibold text-heading">Boards</h3>
        {boards.length === 0 ? (
          <div className="mt-3 flex flex-col items-center gap-1.5 rounded-panel border border-dashed border-edge px-4 py-6 text-center">
            <span
              aria-hidden="true"
              className="flex size-9 items-center justify-center rounded-full bg-primary/10 text-primary"
            >
              <SquareKanban size={16} />
            </span>
            <p className="text-sm font-medium text-heading">No boards yet</p>
            <p className="text-xs text-faint">Create your first board from the Board page.</p>
          </div>
        ) : (
          <ul className="mt-3 space-y-1.5">
            {boards.map((b) => (
              <li key={b.id}>
                <Link
                  href={`/projects/${pid}/board`}
                  className="group flex items-center gap-2.5 rounded-panel border border-edge bg-elevated px-3 py-2 text-sm text-body transition-colors hover:border-edge-strong hover:bg-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent motion-reduce:transition-none"
                >
                  <SquareKanban size={15} className="shrink-0 text-muted" aria-hidden="true" />
                  <span className="min-w-0 flex-1 truncate">{b.name}</span>
                  <ArrowRight
                    size={14}
                    aria-hidden="true"
                    className="shrink-0 text-faint transition-transform group-hover:translate-x-0.5 motion-reduce:transition-none"
                  />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}