"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { currentUserSafe, logout } from "@/lib/auth";
import { get, post } from "@/lib/api";
import type { ApiPage, Project, User } from "@/types";

export default function DashboardPage() {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const loadProjects = useCallback(async () => {
    const page = await get<ApiPage<Project>>("/api/v1/projects?page=0&size=50");
    setProjects(page.content);
  }, []);

  useEffect(() => {
    currentUserSafe().then((u) => {
      if (!u) {
        router.replace("/login");
        return;
      }
      setUser(u);
      loadProjects();
    });
  }, [router, loadProjects]);

  const createProject = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    await post<Project>("/api/v1/projects", { name: name.trim(), description });
    setName("");
    setDescription("");
    loadProjects();
  };

  const handleLogout = async () => {
    await logout();
    router.replace("/login");
  };

  return (
    <main className="mx-auto max-w-4xl p-8">
      <header className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Dashboard</h1>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-600">{user?.name}</span>
          <button className="rounded border border-slate-300 px-3 py-1 text-sm" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </header>

      <form onSubmit={createProject} className="mb-6 rounded-lg border border-slate-300 bg-white p-4 flex gap-2">
        <input
          className="flex-1 rounded border border-slate-300 px-2 py-2"
          placeholder="New project name"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <input
          className="flex-1 rounded border border-slate-300 px-2 py-2"
          placeholder="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <button className="rounded bg-slate-700 px-4 py-2 text-white" type="submit">
          Create
        </button>
      </form>

      {projects.length === 0 ? (
        <p className="text-slate-500">No projects yet. Create your first one above.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {projects.map((project) => (
            <li key={project.id}>
              <a
                href={`/projects/${project.id}`}
                className="block rounded-lg border border-slate-300 bg-white p-4 shadow-sm hover:border-slate-500"
              >
                <p className="font-medium text-slate-800">{project.name}</p>
                {project.description && <p className="mt-1 text-sm text-slate-500">{project.description}</p>}
              </a>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}