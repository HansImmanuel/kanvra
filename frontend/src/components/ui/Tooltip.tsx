import type { ReactNode } from "react";

/**
 * Pure-CSS tooltip for icon-only controls. Shows on hover and on keyboard
 * focus (focus-within), so the hint is reachable without a pointer. The
 * wrapped control keeps its own aria-label/accessible name — the bubble is
 * decorative duplication for sighted users.
 */
interface TooltipProps {
  label: string;
  children: ReactNode;
  /** Render the bubble below the trigger instead of above. */
  placement?: "top" | "bottom";
}

export default function Tooltip({ label, children, placement = "top" }: TooltipProps) {
  return (
    <span className="group/tt relative inline-flex">
      {children}
      <span
        role="tooltip"
        className={
          "pointer-events-none absolute left-1/2 z-50 -translate-x-1/2 whitespace-nowrap rounded bg-primary px-2 py-1 " +
          "text-xs font-medium text-white opacity-0 shadow-overlay transition-opacity duration-150 " +
          "group-hover/tt:opacity-100 group-focus-within/tt:opacity-100 motion-reduce:transition-none " +
          (placement === "top" ? "bottom-full mb-1.5" : "top-full mt-1.5")
        }
      >
        {label}
      </span>
    </span>
  );
}
