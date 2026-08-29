"use client";

import dynamic from "next/dynamic";
import { use, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  ArrowLeft,
  LayoutDashboard,
  Settings,
  SquareKanban,
  TrendingUp,
  TriangleAlert,
  X
} from "lucide-react";
import { get } from "@/lib/api";
import NotificationBell from "@/components/notification/NotificationBell";
import type { Project } from "@/types";

// On-demand: settings opens from the header, so it isn't needed at first paint
// (phase 10 lazy loading).
const ProjectSettingsPanel = dynamic(
  () => import("@/components/project/ProjectSettingsPanel")
);

/**
 * Persistent shell for the project area (docs/PRD.md §7). The left sidebar
 * (Board / Dashboard / Analytics) stays visible while navigating between
 * project-level routes; it carries the Kanvra brand mark and the project
 * identity block, collapsing to an icons-only rail below the md breakpoint.
 * The sticky header shows an eyebrow with the project name plus the active
 * page title, settings, and the notification bell. The 401 guard lives here
 * once, shared by every project sub-page. All styling uses the design tokens
 * registered in app/globals.css (@theme) mirroring src/lib/design-tokens.ts.
 */
export default function ProjectLayout({
  params,
  children
}: Readonly<{ params: Promise<{ projectId: string }>; children: React.ReactNode }>) {
  const router = useRouter();
  const pathname = usePathname();
  const { projectId } = use(params);
  const pid = Number(projectId);

  const [project, setProject] = useState<Project | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [showSettings, setShowSettings] = useState(false);

  const loadProject = useCallback(async () => {
    try {
      const proj = await get<Project>(`/api/v1/projects/${pid}`);
      setProject(proj);
    } catch (err) {
      if (err instanceof Error && "status" in err && (err as { status: number }).status === 401) {
        router.replace("/login");
        return;
      }
      setLoadError(err instanceof Error ? err.message : "Failed to load project");
    }
  }, [pid, router]);

  useEffect(() => {
    void loadProject();
  }, [loadProject]);

  const isActive = (href: string) =>
    pathname === href || pathname.startsWith(href + "/");

  const navItems = [
    { href: `/projects/${pid}/board`, label: "Board", icon: SquareKanban },
    { href: `/projects/${pid}/dashboard`, label: "Dashboard", icon: LayoutDashboard },
    { href: `/projects/${pid}/analytics`, label: "Analytics", icon: TrendingUp }
  ];

  // Header title hierarchy: eyebrow shows the project, h1 the active page.
  const pageTitle = navItems.find((item) => isActive(item.href))?.label ?? "Project";
  const projectInitial =
    (project?.name ?? "P").trim().charAt(0).toUpperCase() || "P";

  // Shared accessible focus treatment for all interactive shell controls.
  const focusRing =
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

  return (
    <div className="flex min-h-screen">
      {/*
        Persistent left sidebar: icons-only rail under md, full width from md up.
        Width transitions smoothly; motion is disabled for reduced-motion users.
      */}
      <nav
        aria-label="Project navigation"
        className="sticky top-0 z-30 flex h-screen w-14 shrink-0 flex-col border-r border-edge bg-elevated transition-[width] duration-200 ease-out motion-reduce:transition-none md:w-sidebar"
      >
        {/* Branding area (full sidebar) — the wordmark links back to projects. */}
        <div className="hidden border-b border-edge px-2.5 py-4 md:block">
          <Link
            href="/dashboard"
            title="All projects"
            className={`-mx-1 flex items-center gap-2 rounded-panel px-1 py-0.5 transition-colors hover:bg-primary/5 motion-reduce:transition-none ${focusRing}`}
          >
            <span
              aria-hidden="true"
              className="flex size-6 shrink-0 items-center justify-center rounded-md bg-primary text-[11px] font-bold text-white"
            >
              K
            </span>
            <span className="text-sm font-semibold tracking-tight text-heading">Kanvra</span>
          </Link>

          {/* Project identity: monogram + name, clipped to one line. */}
          <div className="mt-3 flex items-center gap-2 px-1" title={project?.name}>
            <span
              aria-hidden="true"
              className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[11px] font-bold text-primary"
            >
              {projectInitial}
            </span>
            <p className="min-w-0 truncate text-sm font-medium text-body">
              {project?.name ?? "…"}
            </p>
          </div>
        </div>

        {/* Compact brand mark for the collapsed rail. */}
        <div className="flex justify-center border-b border-edge py-3 md:hidden">
          <span
            aria-hidden="true"
            className="flex size-7 items-center justify-center rounded-md bg-primary text-[11px] font-bold text-white"
          >
            K
          </span>
        </div>

        {/* Primary navigation: Board / Dashboard / Analytics */}
        <ul className="flex flex-col items-center gap-1 p-2 md:items-stretch">
          {navItems.map((item) => {
            const active = isActive(item.href);
            return (
              <li key={item.href} className="w-full">
                <Link
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  aria-label={item.label}
                  title={item.label}
                  className={
                    "flex items-center justify-center gap-2.5 rounded-panel px-2.5 py-2 " +
                    "text-sm transition-colors duration-150 motion-reduce:transition-none " +
                    "md:justify-start focus-visible:-outline-offset-2 " +
                    (active
                      ? "bg-primary/10 font-medium text-body"
                      : "text-dim hover:bg-primary/5 hover:text-body") +
                    ` ${focusRing}`
                  }
                >
                  <span
                    aria-hidden="true"
                    className={`h-1.5 w-1.5 shrink-0 rounded-full ${
                      active ? "bg-primary" : "bg-transparent"
                    }`}
                  />
                  <item.icon size={16} className="shrink-0" aria-hidden="true" />
                  <span className="hidden md:inline">{item.label}</span>
                </Link>
              </li>
            );
          })}
        </ul>

        {/* Escape hatch back to the global dashboard. */}
        <div className="mt-auto border-t border-edge p-2">
          <Link
            href="/dashboard"
            title="All projects"
            className={
              "flex items-center justify-center gap-2.5 rounded-panel px-2.5 py-2 text-sm " +
              "transition-colors duration-150 hover:bg-primary/5 motion-reduce:transition-none " +
              "md:justify-start " +
              focusRing
            }
          >
            <ArrowLeft size={16} className="shrink-0 text-faint" aria-hidden="true" />
            <span className="hidden text-muted md:inline">All projects</span>
          </Link>
        </div>
      </nav>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Sticky toolbar: eyebrow = project identity, h1 = current page. */}
        <header className="sticky top-0 z-20 flex items-center justify-between gap-4 border-b border-edge bg-surface px-6 py-3">
          <div className="min-w-0">
            <p className="truncate text-[11px] font-medium uppercase tracking-wide text-muted">
              {project?.name ?? "Project"}
            </p>
            <h1 className="truncate text-lg font-semibold leading-tight text-heading">
              {pageTitle}
            </h1>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            {project && (
              <button
                type="button"
                onClick={() => setShowSettings(true)}
                className={
                  "inline-flex items-center gap-1.5 rounded-panel border border-transparent " +
                  "px-2.5 py-1.5 text-sm text-dim transition-colors duration-150 " +
                  "hover:border-edge hover:bg-elevated hover:text-body motion-reduce:transition-none " +
                  focusRing
                }
              >
                <Settings size={15} aria-hidden="true" />
                <span className="hidden sm:inline">Settings</span>
              </button>
            )}
            <NotificationBell />
          </div>
        </header>

        {loadError && (
          <div
            role="alert"
            className="mx-6 mt-4 flex items-start gap-2.5 rounded-panel border border-red-300 bg-red-100 p-3 text-sm text-red-800"
          >
            <TriangleAlert size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span className="min-w-0 flex-1">{loadError}</span>
            <button
              type="button"
              aria-label="Dismiss error"
              onClick={() => setLoadError(null)}
              className={`shrink-0 rounded p-0.5 text-red-700 transition-colors hover:bg-red-200 focus-visible:outline-offset-0 motion-reduce:transition-none ${focusRing}`}
            >
              <X size={14} aria-hidden="true" />
            </button>
          </div>
        )}

        <main className="flex-1 p-6">{children}</main>
      </div>

      {showSettings && project && (
        <ProjectSettingsPanel project={project} onClose={() => setShowSettings(false)} />
      )}
    </div>
  );
}