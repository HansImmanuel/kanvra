"use client";

import { useCallback, useEffect, useState } from "react";
import { Button, Input, Modal, Avatar, LabelChip, Select } from "@/components/ui";
import { currentUserSafe } from "@/lib/auth";
import {
  addMember,
  createLabel,
  deleteLabel,
  listMembers,
  listProjectLabels,
  removeMember,
  updateLabel
} from "@/lib/actions";
import type { Label, Member, Project, User } from "@/types";

/** Preset palette for label colors (hex, matches backend `#RRGGBB` rule). */
const PALETTE = ["#2563EB", "#DC2626", "#059669", "#D97706", "#7C3AED", "#DB2777", "#0891B2", "#4B5563"];

interface ProjectSettingsPanelProps {
  project: Project;
  onClose: () => void;
}

/**
 * Project settings slide-over (docs/PRD.md "Project settings", SPEC §4 + §9):
 * member management (OWNER-gated) and label CRUD.
 *
 * Gating: OWNER rights are derived server-side on every mutation; the UI only
 * hides the controls for MEMBERs (never a security boundary).
 */
export default function ProjectSettingsPanel({ project, onClose }: ProjectSettingsPanelProps) {
  const [me, setMe] = useState<User | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [labels, setLabels] = useState<Label[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newMemberId, setNewMemberId] = useState("");
  const [newMemberRole, setNewMemberRole] = useState("MEMBER");
  const [newLabelName, setNewLabelName] = useState("");
  const [newLabelColor, setNewLabelColor] = useState(PALETTE[0]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [editColor, setEditColor] = useState(PALETTE[0]);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    try {
      const [memberPage, labelList] = await Promise.all([
        listMembers(project.id, 0, 100),
        listProjectLabels(project.id)
      ]);
      setMembers(memberPage.content);
      setLabels(labelList);
      setError(null);
    } catch {
      setError("Failed to load settings");
    }
  }, [project.id]);

  useEffect(() => {
    setLoading(true);
    void Promise.all([reload(), currentUserSafe().then(setMe)]).finally(() => setLoading(false));
  }, [reload]);

  const myRole = members.find((m) => m.id === me?.id)?.role ?? null;
  const isOwner = me != null && (myRole === "OWNER" || project.ownerId === me.id);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      await reload();
    } catch {
      setError("Action failed — check permissions and retry");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Settings — ${project.name}`} widthClass="max-w-2xl">
      {loading ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="space-y-6">
          {error && <p className="rounded bg-red-100 border border-red-300 p-2 text-sm">{error}</p>}

          {/* --- Members (SPEC §4) --- */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-700">Members</h3>
            <ul className="space-y-1">
              {members.map((m) => (
                <li key={m.id} className="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 p-2 text-sm">
                  <Avatar name={m.name} size={22} />
                  <span className="font-medium text-slate-700">{m.name}</span>
                  <span className="rounded bg-slate-200 px-1.5 text-xs text-slate-600">{m.role}</span>
                  {isOwner && m.id !== project.ownerId && (
                    <Button
                      variant="dangerLink"
                      className="ml-auto"
                      disabled={busy}
                      aria-label={`Remove ${m.name}`}
                      onClick={() => void run(() => removeMember(project.id, m.id))}
                    >
                      Remove
                    </Button>
                  )}
                </li>
              ))}
            </ul>

            {isOwner && (
              <form
                className="mt-2 flex items-end gap-2"
                onSubmit={(e) => {
                  e.preventDefault();
                  if (!newMemberId.trim()) return;
                  void run(() =>
                    addMember(project.id, Number(newMemberId), newMemberRole as "OWNER" | "MEMBER")
                  ).then(() => setNewMemberId(""));
                }}
              >
                <Input
                  fieldSize="sm"
                  style={{ width: "7rem" }}
                  aria-label="User id"
                  placeholder="User id"
                  inputMode="numeric"
                  value={newMemberId}
                  onChange={(e) => setNewMemberId(e.target.value)}
                />
                <Select
                  label=""
                  value={newMemberRole}
                  onChange={setNewMemberRole}
                  options={[
                    { value: "MEMBER", label: "MEMBER" },
                    { value: "OWNER", label: "OWNER" }
                  ]}
                />
                <Button
                  variant="primary"
                  size="md"
                  disabled={busy || !newMemberId.trim()}
                  type="submit"
                >
                  Add member
                </Button>
              </form>
            )}
          </section>

          {/* --- Labels (SPEC §9) --- */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-700">Labels</h3>
            <ul className="space-y-1">
              {labels.map((l) =>
                editingId === l.id ? (
                  <li key={l.id} className="rounded border border-slate-200 p-2">
                    <div className="flex items-center gap-2">
                      <Input
                        fieldSize="sm"
                        style={{ width: "10rem" }}
                        aria-label="Edit label name"
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                      />
                      <PalettePicker value={editColor} onChange={setEditColor} />
                      <span className="ml-auto flex gap-1">
                        <Button
                          variant="primary"
                          size="sm"
                          disabled={busy}
                          onClick={() =>
                            void run(() => updateLabel(l.id, editName.trim() || l.name, editColor)).then(() =>
                              setEditingId(null)
                            )
                          }
                        >
                          Save
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setEditingId(null)}
                        >
                          Cancel
                        </Button>
                      </span>
                    </div>
                  </li>
                ) : (
                  <li key={l.id} className="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 p-2">
                    <LabelChip name={l.name} color={l.color} />
                    <span className="ml-auto flex gap-1">
                      <Button
                        variant="link"
                        onClick={() => {
                          setEditingId(l.id);
                          setEditName(l.name);
                          setEditColor(l.color);
                        }}
                      >
                        Edit
                      </Button>
                      <Button
                        variant="dangerLink"
                        disabled={busy}
                        aria-label={`Delete ${l.name}`}
                        onClick={() => void run(() => deleteLabel(l.id))}
                      >
                        Delete
                      </Button>
                    </span>
                  </li>
                )
              )}
              {labels.length === 0 && <li className="text-sm text-slate-400">No labels yet.</li>}
            </ul>

            <form
              className="mt-2 flex items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                if (!newLabelName.trim()) return;
                void run(() => createLabel(project.id, newLabelName.trim(), newLabelColor)).then(() =>
                  setNewLabelName("")
                );
              }}
            >
              <Input
                fieldSize="sm"
                style={{ width: "11rem" }}
                aria-label="New label name"
                placeholder="New label name"
                value={newLabelName}
                onChange={(e) => setNewLabelName(e.target.value)}
              />
              <PalettePicker value={newLabelColor} onChange={setNewLabelColor} />
              <Button
                variant="primary"
                size="md"
                type="submit"
                disabled={busy || !newLabelName.trim()}
              >
                Create label
              </Button>
            </form>
          </section>

          <p className="text-xs text-slate-400">
            Member changes require the project OWNER role; the server enforces this on every request.
          </p>
        </div>
      )}
    </Modal>
  );
}

function PalettePicker({ value, onChange }: { value: string; onChange: (color: string) => void }) {
  return (
    <div className="flex items-center gap-1 pb-1" role="group" aria-label="Pick a color">
      {PALETTE.map((c) => (
        <button
          key={c}
          type="button"
          aria-label={`Use color ${c}`}
          aria-pressed={value === c}
          className={"h-5 w-5 rounded-full border " + (value === c ? "border-slate-800" : "border-transparent")}
          style={{ backgroundColor: c }}
          onClick={() => onChange(c)}
        />
      ))}
    </div>
  );
}