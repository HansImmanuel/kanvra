"use client";

import { use, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { get } from "@/lib/api";
import NotificationBell from "@/components/notification/NotificationBell";
import ProjectSettingsPanel from "@/components/project/ProjectSettingsPanel";
import type { Project } from "@/types";

/**
 * Persistent shell for the project area (docs/PRD.md §7, rewired to a
 * side-navigated structure). The left sidebar (Board / Dashboard / Analytics)
 * stays visible while navigating between project-level routes; the header hosts
 * the project name, settings, and the notification bell. The 401 guard lives
 * here once, shared by every project sub-page.
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

  const navItems = [
    { href: `/projects/${pid}/board`, label: "Board" },
    { href: `/projects/${pid}/dashboard`, label: "Dashboard" },
    { href: `/projects/${pid}/analytics`, label: "Analytics" }
  ];

  const isActive = (href: string) =>
    pathname === href || pathname.startsWith(href + "/");

  return (
    <div className="flex min-h-screen">
      {/* Persistent left sidebar */}
      <nav aria-label="Project navigation" className="flex w-48 shrink-0 flex-col border-r border-slate-200 bg-slate-50">
        <Link
          href="/dashboard"
          className="border-b border-slate-200 px-4 py-2 text-sm text-slate-500 hover:bg-slate-100"
        >
          ← Dashboard
        </Link>
        <ul className="flex flex-col gap-1 p-2">
          {navItems.map((item) => {
            const active = isActive(item.href);
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={`block rounded px-3 py-2 text-sm ${
                    active
                      ? "bg-slate-200 font-medium text-slate-900"
                      : "text-slate-600 hover:bg-slate-100"
                  }`}
                >
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
          <h1 className="truncate text-xl font-bold text-slate-800">
            {project?.name ?? "Project"}
          </h1>
          <div className="flex items-center gap-2">
            {project && (
              <button
                type="button"
                className="rounded border border-slate-300 px-3 py-1 text-sm text-slate-600 hover:border-slate-500"
                onClick={() => setShowSettings(true)}
              >
                ⚙ Settings
              </button>
            )}
            <NotificationBell />
          </div>
        </header>

        {loadError && (
          <p className="mx-6 mt-4 rounded bg-red-100 border border-red-300 p-3 text-sm">{loadError}</p>
        )}

        <main className="flex-1 p-6">{children}</main>
      </div>

      {showSettings && project && (
        <ProjectSettingsPanel project={project} onClose={() => setShowSettings(false)} />
      )}
    </div>
  );
}