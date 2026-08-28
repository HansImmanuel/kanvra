import type { ReactNode } from "react";
import { colors, type StatusTone } from "@/lib/design-tokens";

/**
 * Shared status badge (docs/TECH_DOC.md §14 "ui atoms" — redesign foundation).
 * Tone colors come from the design tokens ({@link colors.status}), which
 * reproduce the priority chip palette hardcoded in TaskCard today.
 */

type BadgeTone = StatusTone;

interface BadgeProps {
  tone?: BadgeTone;
  /** Optional leading glyph (e.g. a priority-direction arrow). Decorative. */
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
}

export default function Badge({ tone = "none", icon, children, className = "" }: BadgeProps) {
  const { fg, bg } = colors.status[tone];
  return (
    <span
      className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs font-medium select-none ${className}`.trim()}
      style={{ backgroundColor: bg, color: fg }}
    >
      {icon != null && (
        <span aria-hidden="true" className="-ml-0.5 inline-flex shrink-0">
          {icon}
        </span>
      )}
      {children}
    </span>
  );
}
