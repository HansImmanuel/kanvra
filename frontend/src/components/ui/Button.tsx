"use client";

import type { ButtonHTMLAttributes } from "react";

/**
 * Shared button (docs/TECH_DOC.md §14 "ui atoms" — redesign foundation).
 *
 * Variants replicate the class combinations currently written inline across the
 * codebase, so swapping an inline button for <Button> is visually identical.
 * `size` only applies to variants that take padding from their size
 * (`primary`, `outline`, `danger`, `dashed`); the compact text-link style
 * variants (`ghost`, `link`, `dangerLink`) carry their own padding.
 */

export type ButtonVariant =
  | "primary"
  | "outline"
  | "ghost"
  | "danger"
  | "dashed"
  | "link"
  | "dangerLink"
  | "icon"
  | "iconDanger";

export type ButtonSize = "sm" | "compact" | "md";

/** Color + border + state classes, shared by both sizes of a variant. */
const VARIANTS: Record<ButtonVariant, { color: string; sized: boolean }> = {
  primary: {
    color: "bg-slate-700 text-white hover:bg-slate-600 disabled:opacity-50",
    sized: true
  },
  outline: {
    color:
      "border border-slate-300 bg-white text-slate-600 hover:border-slate-500 disabled:opacity-50",
    sized: true
  },
  ghost: {
    color: "px-2 py-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40",
    sized: false
  },
  danger: {
    color: "bg-red-600 text-white hover:bg-red-700 disabled:opacity-40",
    sized: true
  },
  dashed: {
    color:
      "border border-dashed border-slate-400 bg-transparent px-3 py-2 text-sm text-slate-500 hover:border-slate-600 hover:text-slate-700 disabled:opacity-50",
    sized: false
  },
  link: {
    color: "px-1 text-xs text-slate-500 hover:bg-slate-200 hover:text-slate-700 disabled:opacity-50",
    sized: false
  },
  dangerLink: {
    color:
      "px-1 text-xs text-slate-500 hover:bg-red-100 hover:text-red-700 disabled:opacity-50",
    sized: false
  },
  icon: {
    color: "px-1 text-slate-500 hover:text-slate-800 disabled:opacity-30",
    sized: false
  },
  iconDanger: {
    color: "px-1 text-slate-500 hover:text-red-600 disabled:opacity-30",
    sized: false
  }
};

const SIZES: Record<ButtonSize, string> = {
  sm: "px-2 py-1 text-xs",
  compact: "px-2 py-1 text-sm",
  md: "px-3 py-1.5 text-sm"
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

export default function Button({
  variant = "primary",
  size = "md",
  type = "button",
  className = "",
  children,
  ...rest
}: ButtonProps) {
  const v = VARIANTS[variant];
  const sizing = v.sized ? SIZES[size] : "";
  return (
    <button
      type={type}
      className={`rounded ${v.color} ${sizing} ${className}`.trim()}
      {...rest}
    >
      {children}
    </button>
  );
}
