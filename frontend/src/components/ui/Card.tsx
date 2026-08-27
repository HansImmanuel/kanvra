"use client";

import type { HTMLAttributes } from "react";

/**
 * Shared card surface (docs/TECH_DOC.md §14 "ui atoms" — redesign foundation).
 * Replicates the panel/card treatment used on the dashboard pages and panels:
 * white surface, slate-300 border, rounded-lg. Shadow is opt-in because only
 * task cards and clickable project links carry one today.
 */
interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Adds shadow-sm (used for interactive/raised cards). */
  raised?: boolean;
}

export default function Card({ raised = false, className = "", children, ...rest }: CardProps) {
  return (
    <div
      className={`rounded-lg border border-slate-300 bg-white p-4${raised ? " shadow-sm" : ""} ${className}`.trim()}
      {...rest}
    >
      {children}
    </div>
  );
}
