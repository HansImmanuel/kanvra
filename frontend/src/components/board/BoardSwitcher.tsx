"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui";
import type { BoardRef } from "@/types";

interface BoardSwitcherProps {
  boards: BoardRef[];
  activeId: number | null;
  onSelect: (boardId: number) => void;
  onCreate: (name: string) => void;
  busy?: boolean;
}

/**
 * Board tabs + inline create form (docs/SPEC.md §5). The parent owns loading
 * and selection; this is a controlled presentation component.
 */
export default function BoardSwitcher({ boards, activeId, onSelect, onCreate, busy = false }: BoardSwitcherProps) {
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    onCreate(trimmed);
    setName("");
    setCreating(false);
  };

  return (
    <div className="mb-3 flex flex-wrap items-center gap-2" data-testid="board-switcher">
      {boards.map((b) => (
        <button
          key={b.id}
          type="button"
          aria-current={b.id === activeId}
          className={
            "rounded-full px-3 py-1 text-sm border transition-colors " +
            (b.id === activeId
              ? "border-slate-800 bg-slate-800 text-white"
              : "border-slate-300 bg-white text-slate-600 hover:border-slate-500")
          }
          onClick={() => onSelect(b.id)}
        >
          {b.name}
        </button>
      ))}

      {creating ? (
        <form onSubmit={submit} className="flex items-center gap-1">
          <input
            aria-label="New board name"
            autoFocus
            placeholder="Board name"
            className="w-40 rounded border border-slate-300 px-2 py-1 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <Button type="submit" variant="primary" size="sm" disabled={busy || !name.trim()}>
            Create
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setCreating(false);
              setName("");
            }}
          >
            Cancel
          </Button>
        </form>
      ) : (
        <button
          type="button"
          disabled={busy}
          className="rounded-full border border-dashed border-slate-400 px-3 py-1 text-sm text-slate-500 hover:border-slate-600 hover:text-slate-700 disabled:opacity-50"
          onClick={() => setCreating(true)}
        >
          + New board
        </button>
      )}
    </div>
  );
}