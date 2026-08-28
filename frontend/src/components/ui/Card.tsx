"use client";

import type { HTMLAttributes } from "react";

/**
 * Shared card surface (docs/TECH_DOC.md §14 "ui atoms" — redesign foundation).
 * Replicates the panel/card treatment used on the dashboard pages and panels.
 * Shadow is opt-in because only task cards and clickable project links carry
 * one today; padding density is selectable (sm/md/lg) instead of overridden
 * via conflicting utility classes.
 */
const PADS = {
  sm: "p-3",
  md: "p-4",
  lg: "p-5"
} as const;

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Adds shadow-sm (used for interactive/raised cards). */
  raised?: boolean;
  /** Padding density — `lg` for feature sections, `md` (default) for panels. */
  pad?: keyof typeof PADS;
}

export default function Card({
  raised = false,
  pad = "md",
  className = "",
  children,
  ...rest
}: CardProps) {
  return (
    <div
      className={`rounded-panel border border-edge-strong bg-surface ${PADS[pad]}${
        raised ? " shadow-sm" : ""
      } ${className}`.trim()}
      {...rest}
    >
      {children}
    </div>
  );
}
