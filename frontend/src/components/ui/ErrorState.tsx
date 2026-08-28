"use client";

import { CircleAlert, RotateCcw } from "lucide-react";
import Button from "./Button";

/**
 * Failed-request banner with an optional retry action. The message stays a
 * single span so callers (and tests) can assert on the exact error text.
 */
export default function ErrorState({
  message,
  onRetry,
  className = ""
}: {
  message: string;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={`flex flex-wrap items-center gap-x-3 gap-y-2 rounded-panel border border-red-300 bg-red-100 p-3 text-sm text-red-800 ${className}`.trim()}
    >
      <CircleAlert size={15} className="shrink-0" aria-hidden="true" />
      <span className="min-w-0 flex-1">{message}</span>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RotateCcw size={13} aria-hidden="true" /> Retry
        </Button>
      )}
    </div>
  );
}
