"use client";

import { useCallback, useEffect, useState } from "react";
import { Avatar, Button, Textarea } from "@/components/ui";
import { currentUserSafe } from "@/lib/auth";
import { realtime } from "@/lib/websocket";
import { relativeTime } from "@/lib/time";
import { createComment, deleteComment, listComments, updateComment } from "@/lib/actions";
import type { Comment, User } from "@/types";

interface CommentsThreadProps {
  taskId: number;
}

/**
 * Comment thread for a task (docs/SPEC.md §8): paginated oldest-first list,
 * composer, and author-only edit/delete. Rendering uses plain JSX text nodes —
 * never {@code dangerouslySetInnerHTML} — so user content cannot inject markup.
 *
 * Realtime: any {@code comment.*} event for this task triggers an authoritative
 * re-fetch of the thread. Comment responses carry no eventId, so own echoes are
 * handled by the reload itself (it lands on identical data), not by dedup.
 */
export default function CommentsThread({ taskId }: CommentsThreadProps) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editText, setEditText] = useState("");
  const [me, setMe] = useState<User | null>(null);

  const reload = useCallback(
    async (targetPage = 0) => {
      try {
        const result = await listComments(taskId, targetPage);
        setComments((prev) => (targetPage === 0 ? result.content : [...prev, ...result.content]));
        setPage(result.page);
        setTotalPages(result.totalPages);
        setError(null);
      } catch {
        setError("Failed to load comments");
      }
    },
    [taskId]
  );

  useEffect(() => {
    setLoading(true);
    void Promise.all([reload(0), currentUserSafe().then(setMe)]).finally(() => setLoading(false));
  }, [reload]);

  // Remote comment events refresh the thread; own echoes land on identical data.
  useEffect(() => {
    const handler = (event: { type?: unknown; payload?: Record<string, unknown> }) => {
      if (typeof event.type !== "string" || !event.type.startsWith("COMMENT_")) return;
      const eventTaskId = event.payload?.taskId;
      if (eventTaskId != null && Number(eventTaskId) !== taskId) return;
      void reload(0);
    };
    realtime.on(handler as never);
    return () => {
      realtime.off(handler as never);
    };
  }, [reload, taskId]);

  const submit = async () => {
    const content = draft.trim();
    if (!content) return;
    setBusy(true);
    setError(null);
    try {
      await createComment(taskId, content);
      setDraft("");
      await reload(0);
    } catch {
      setError("Failed to post comment");
    } finally {
      setBusy(false);
    }
  };

  const saveEdit = async (commentId: number) => {
    const content = editText.trim();
    if (!content) return;
    setBusy(true);
    setError(null);
    try {
      await updateComment(commentId, content);
      setEditingId(null);
      await reload(0);
    } catch {
      setError("Failed to update comment");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (commentId: number) => {
    setBusy(true);
    setError(null);
    try {
      await deleteComment(commentId);
      await reload(0);
    } catch {
      setError("Failed to delete comment");
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return <p className="text-sm text-slate-500">Loading comments…</p>;
  }

  return (
    <div className="space-y-2">
      <p className="text-sm font-semibold text-slate-700">Comments</p>

      <ul className="space-y-2">
        {comments.map((c) => (
          <li key={c.id} className="rounded border border-slate-200 bg-slate-50 p-2 text-sm">
            <div className="flex items-center gap-2">
              <Avatar name={c.author.name} avatarUrl={c.author.avatarUrl} size={20} />
              <span className="font-medium text-slate-700">{c.author.name}</span>
              <span className="text-xs text-slate-400">
                {relativeTime(c.createdAt)}
                {c.updatedAt !== c.createdAt ? " (edited)" : ""}
              </span>
              {me?.id === c.author.id && editingId !== c.id && (
                <span className="ml-auto flex gap-1">
                  <Button
                    variant="link"
                    onClick={() => {
                      setEditingId(c.id);
                      setEditText(c.content);
                    }}
                  >
                    Edit
                  </Button>
                  <Button
                    variant="dangerLink"
                    disabled={busy}
                    onClick={() => void remove(c.id)}
                  >
                    Delete
                  </Button>
                </span>
              )}
            </div>

            {editingId === c.id ? (
              <div className="mt-1 space-y-1">
                <Textarea
                  aria-label="Edit comment"
                  fieldSize="sm"
                  className="min-h-12"
                  value={editText}
                  onChange={(e) => setEditText(e.target.value)}
                />
                <div className="flex gap-2">
                  <Button variant="primary" size="sm" disabled={busy} onClick={() => void saveEdit(c.id)}>
                    Save
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setEditingId(null)}>
                    Cancel
                  </Button>
                </div>
              </div>
            ) : (
              // Plain JSX text node — React escapes user content; no HTML injection path.
              <p className="mt-1 whitespace-pre-wrap text-slate-700">{c.content}</p>
            )}
          </li>
        ))}
        {comments.length === 0 && <li className="text-sm text-slate-400">No comments yet.</li>}
      </ul>

      {page + 1 < totalPages && (
        <Button variant="outline" size="sm" disabled={busy} onClick={() => void reload(page + 1)}>
          Load more
        </Button>
      )}

      {error && <p className="rounded bg-red-100 border border-red-300 p-2 text-xs">{error}</p>}

      <div className="flex gap-2 pt-1">
        <Textarea
          aria-label="New comment"
          placeholder="Write a comment…"
          fieldSize="sm"
          className="min-h-10 flex-1"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
        />
        <Button
          variant="primary"
          size="md"
          className="self-end"
          disabled={busy || !draft.trim()}
          onClick={() => void submit()}
        >
          Comment
        </Button>
      </div>
    </div>
  );
}