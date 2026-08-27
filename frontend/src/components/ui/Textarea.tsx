"use client";

import { useId, type TextareaHTMLAttributes } from "react";

/**
 * Shared textarea (docs/TECH_DOC.md §14 "ui atoms" — redesign foundation).
 * Mirrors the composer fields used by CommentsThread and TaskDetailModal;
 * min-height and width stay caller-controlled via className.
 * Size scale mirrors the paddings found inline: `md` px-2 py-1.5 (default),
 * `sm` px-2 py-1.
 */
const FIELD_BASE =
  "w-full rounded border border-slate-300 bg-white text-sm text-slate-800 placeholder:text-slate-400 disabled:text-slate-400";

const SIZES = {
  md: "px-2 py-1.5",
  sm: "px-2 py-1"
} as const;

interface TextareaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "size"> {
  label?: string;
  /** Visual density of the field. */
  fieldSize?: keyof typeof SIZES;
}

export default function Textarea({
  label,
  id,
  fieldSize = "md",
  className = "",
  ...rest
}: TextareaProps) {
  const autoId = useId();
  const fieldId = id ?? autoId;
  const classes = `${FIELD_BASE} ${SIZES[fieldSize]} ${className}`.trim();

  if (label == null) {
    return <textarea id={fieldId} className={classes} {...rest} />;
  }

  return (
    <label htmlFor={fieldId} className="block text-sm">
      <span className="mb-1 block font-medium text-slate-600">{label}</span>
      <textarea id={fieldId} className={classes} {...rest} />
    </label>
  );
}
