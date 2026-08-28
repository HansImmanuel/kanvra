"use client";

import { useEffect, useState } from "react";
import { CircleAlert, CircleCheck, Loader2 } from "lucide-react";
import { Button, Input, LabelChip, Modal, Select, Textarea } from "@/components/ui";
import { getTask, updateTask, listMembers, listProjectLabels } from "@/lib/actions";
import { ApiError } from "@/lib/api";
import { noteLocalEventId } from "@/lib/websocket";
import CommentsThread from "@/components/comment/CommentsThread";
import type { ApiPage, Label, Member, TaskCard, TaskResponse } from "@/types";

const EMPTY_MEMBER_PAGE: ApiPage<Member> = {
  content: [],
  page: 0,
  size: 0,
  totalElements: 0,
  totalPages: 0
};

const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

interface TaskDetailModalProps {
  projectId: number;
  taskId: number;
  /** Board card for read-only assignee/labels display (labels editing lands in TL4). */
  card?: TaskCard;
  onClose: () => void;
  /** Fired after a successful edit so the board reloads from the server. */
  onChanged: () => void;
}

/**
 * Task detail modal (docs/SPEC.md §7, TECH_DOC.md §14 "Board state").
 *
 * Loads the authoritative task (so {@code version} is always current), edits
 * title/description/priority/dueDate, and writes with a version-gated PATCH.
 * On 409 TASK_VERSION_CONFLICT the modal reconciles from the server-provided
 * {@code currentState} (SPEC §7.2) and reloads — it never leaves a stale
 * version in place.
 *
 * Assignee and label *pickers* arrive in Sprint 4 Tasklist 4 (they need the
 * member/label list endpoints); for now those are shown read-only from the
 * card preview.
 */
export default function TaskDetailModal({
  projectId,
  taskId,
  card,
  onClose,
  onChanged
}: TaskDetailModalProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [projectLabels, setProjectLabels] = useState<Label[]>([]);
  const [assignee, setAssignee] = useState("");
  const [selectedLabelIds, setSelectedLabelIds] = useState<number[]>([]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    setNotice(null);
    // Pickers need the project's members + labels; failures degrade to empty
    // lists rather than blocking the core edit flow.
    void Promise.all([
      getTask(taskId),
      listMembers(projectId, 0, 100).catch(() => EMPTY_MEMBER_PAGE),
      listProjectLabels(projectId).catch(() => [] as Label[])
    ])
      .then(([t, memberPage, labelList]) => {
        setTitle(t.title ?? "");
        setDescription(t.description ?? "");
        setPriority(t.priority ?? "");
        setDueDate(t.dueDate ?? "");
        setVersion(t.version);
        setMembers(memberPage.content);
        setProjectLabels(labelList);
        setAssignee(t.assigneeId != null ? String(t.assigneeId) : "");
        setSelectedLabelIds(
          t.labelIds ?? (card ? card.labels.map((l) => l.id) : [])
        );
      })
      .catch(() => setError("Failed to load task"))
      .finally(() => setLoading(false));
  }, [taskId, projectId]);

  const applyCurrent = (state: TaskResponse) => {
    setTitle(state.title ?? "");
    setDescription(state.description ?? "");
    setPriority(state.priority ?? "");
    setDueDate(state.dueDate ?? "");
    setVersion(state.version ?? 0);
  };

  const save = async () => {
    if (!title.trim()) {
      setError("Title is required");
      return;
    }
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await updateTask(taskId, {
        title: title.trim(),
        description: description || null,
        priority: priority || null,
        assigneeId: assignee === "" ? null : Number(assignee),
        dueDate: dueDate || null,
        labelIds: selectedLabelIds,
        version
      });
      noteLocalEventId(updated.eventId);
      applyCurrent(updated);
      setNotice("Saved");
      onChanged();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409 && err.data?.currentState) {
        // SPEC §7.2 — reconcile from the server's current state instead of
        // leaving a stale version on screen.
        applyCurrent(err.data.currentState);
        setError("Task was changed by someone else. Reloaded the latest version; please retry.");
        onChanged();
      } else if (err instanceof ApiError) {
        setError(err.message || "Failed to save");
      } else {
        setError("Failed to save");
      }
    } finally {
      setSaving(false);
    }
  };
  return (
    <Modal open onClose={onClose} title={loading ? "Task" : title || "Task"} widthClass="max-w-2xl">
      {loading ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <>
          <div className="grid gap-5 sm:grid-cols-[minmax(0,1fr)_13rem]">
          {/* Main column: identity + discussion */}
          <div className="min-w-0 space-y-3">
            <Input
              label="Title"
              className="w-full"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />

            <Textarea
              label="Description"
              className="w-full min-h-24"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />

            <CommentsThread taskId={taskId} />
          </div>

          {/* Meta rail: pickers stack vertically, collapse under the main column on mobile */}
          <aside className="space-y-3">
            <Select
              label="Priority"
              value={priority}
              onChange={setPriority}
              placeholder="None"
              options={PRIORITIES.map((p) => ({ value: p, label: p }))}
            />

            <Select
              label="Assignee"
              value={assignee}
              onChange={setAssignee}
              placeholder="Unassigned"
              options={members.map((m) => ({ value: String(m.id), label: m.name }))}
            />

            <Input
              type="date"
              label="Due date"
              className="w-full"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
            />

            <div className="text-sm">
              <p className="mb-1 font-medium text-dim">Labels</p>
              {projectLabels.length === 0 ? (
                <span className="text-faint">None available</span>
              ) : (
                <div className="flex flex-wrap gap-1">
                  {projectLabels.map((l) => {
                    const selected = selectedLabelIds.includes(l.id);
                    return (
                      <button
                        key={l.id}
                        type="button"
                        aria-pressed={selected}
                        className={`rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${
                          selected ? "" : "opacity-40"
                        }`}
                        onClick={() =>
                          setSelectedLabelIds((ids) =>
                            ids.includes(l.id) ? ids.filter((id) => id !== l.id) : [...ids, l.id]
                          )
                        }
                      >
                        <LabelChip name={l.name} color={l.color} />
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          </aside>
        </div>

        {error && (
          <p
            role="alert"
            className="flex items-start gap-2 rounded-panel border border-red-300 bg-red-100 p-2 text-sm text-red-800"
          >
            <CircleAlert size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span className="min-w-0 flex-1">{error}</span>
          </p>
        )}
        {notice && !error && (
          <p
            role="status"
            className="flex items-start gap-2 rounded-panel border border-green-300 bg-green-100 p-2 text-sm text-green-800"
          >
            <CircleCheck size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span className="min-w-0 flex-1">{notice}</span>
          </p>
        )}

        <div className="flex items-center justify-between border-t border-edge pt-3">
          <span className="text-xs text-muted">version {version}</span>
          <div className="flex gap-2">
            <Button variant="outline" size="md" onClick={onClose}>
              Close
            </Button>
            <Button variant="primary" size="md" disabled={saving} onClick={() => void save()}>
              {saving && (
                <Loader2
                  size={14}
                  className="mr-1 inline animate-spin motion-reduce:animate-none"
                  aria-hidden="true"
                />
              )}
              {saving ? "Saving…" : "Save"}
            </Button>
          </div>
        </div>
        </>
      )}
    </Modal>
  );
}