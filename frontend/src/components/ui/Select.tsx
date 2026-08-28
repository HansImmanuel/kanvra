"use client";

import { ChevronDown } from "lucide-react";

export interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  disabled?: boolean;
  /** Optional placeholder option shown when value is "". */
  placeholder?: string;
}

/**
 * Styled native {@code <select>} (kept native for keyboard/mobile UX).
 * Operates on string values; callers stringify numeric ids (e.g. assignee
 * id → String(id)). Chevron is decorative; the accessible name comes from
 * the visible label.
 */
export default function Select({
  label,
  value,
  onChange,
  options,
  disabled = false,
  placeholder
}: SelectProps) {
  return (
    <label className="block text-sm">
      {label !== "" && (
        <span className="mb-1 block font-medium text-dim">{label}</span>
      )}
      <span className="relative block">
        <select
          className={
            "w-full appearance-none rounded-panel border border-edge-strong bg-surface py-1.5 pl-2.5 pr-8 " +
            "text-sm text-body transition-colors focus-visible:outline-2 focus-visible:outline-offset-1 " +
            "focus-visible:outline-accent disabled:text-faint motion-reduce:transition-none"
          }
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
        >
          {placeholder != null && <option value="">{placeholder}</option>}
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        <ChevronDown
          size={14}
          aria-hidden="true"
          className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-faint"
        />
      </span>
    </label>
  );
}
