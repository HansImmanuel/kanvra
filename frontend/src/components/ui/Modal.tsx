"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";

// Focusable selectors for trap/tab handling.
const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), ' +
  'select:not([disabled]), [tabindex]:not([tabindex="-1"])';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  /** Title shown in the header edge + used as the dialog's accessible name. */
  title?: string;
  children: ReactNode;
  /** Optional max-width class; defaults to max-w-lg. */
  widthClass?: string;
}

/**
 * Accessible modal dialog (docs/TECH_DOC.md §14 "ui atoms").
 *
 * - Rendered through a portal so it stacks above nav/board chrome.
 * - Focus moves into the dialog on open and is restored on close.
 * - `Escape` and backdrop click close; `Tab`/`Shift+Tab` are trapped.
 * - `aria-modal="true"` + accessible name via `title`.
 *
 * Used by the task detail, project-settings, and notification surfaces.
 */
export default function Modal({ open, onClose, title, children, widthClass = "max-w-lg" }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const previouslyFocused = useRef<Element | null>(null);

  useEffect(() => {
    if (!open) return;
    previouslyFocused.current = document.activeElement;
    dialogRef.current?.focus({ preventScroll: true });

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key !== "Tab") return;
      const dialog = dialogRef.current;
      if (!dialog) return;
      const focusables = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      (previouslyFocused.current as HTMLElement | null)?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="presentation">
      <div
        data-testid="modal-backdrop"
        aria-hidden="true"
        className="absolute inset-0 bg-slate-900/40"
        onClick={onClose}
      />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className={`relative w-full ${widthClass} rounded-xl bg-white p-5 shadow-xl outline-none max-h-[90vh] overflow-y-auto`}
      >
        <div className="mb-3 flex items-start justify-between gap-4">
          {title != null && <h2 className="text-lg font-semibold text-slate-800">{title}</h2>}
          <button
            type="button"
            aria-label="Close dialog"
            className="rounded px-2 py-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
            onClick={onClose}
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body
  );
}