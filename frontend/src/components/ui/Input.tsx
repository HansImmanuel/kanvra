"use client";

import { useId, type InputHTMLAttributes } from "react";

/**
 * Shared text input (docs/TECH_DOC.md §14 "ui atoms" — redesign foundation).
 * Field classes replicate the inline styling used today; with a `label` it
 * renders the same label-above-field wrapper pattern as {@code Select}.
 *
 * Size scale mirrors the paddings found inline across the app:
 * `lg` px-2 py-2 · `md` px-2 py-1.5 (default) · `sm` px-2 py-1 · `xs` px-1 py-0.5.
 */
const FIELD_BASE =
  "rounded border border-slate-300 bg-white text-sm text-slate-800 placeholder:text-slate-400 disabled:text-slate-400";

const SIZES = {
  lg: "px-2 py-2",
  md: "px-2 py-1.5",
  sm: "px-2 py-1",
  xs: "px-1 py-0.5"
} as const;

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "size"> {
  /** When present, renders the field inside a label block with this caption. */
  label?: string;
  /** Visual density of the field (shadows the native `size` attribute name). */
  fieldSize?: keyof typeof SIZES;
}

export default function Input({
  label,
  id,
  fieldSize = "md",
  className = "",
  ...rest
}: InputProps) {
  const autoId = useId();
  const fieldId = id ?? autoId;
  const classes = `${FIELD_BASE} ${SIZES[fieldSize]} ${className}`.trim();

  if (label == null) {
    return <input id={fieldId} className={classes} {...rest} />;
  }

  return (
    <label htmlFor={fieldId} className="block text-sm">
      <span className="mb-1 block font-medium text-slate-600">{label}</span>
      <input id={fieldId} className={classes} {...rest} />
    </label>
  );
}
