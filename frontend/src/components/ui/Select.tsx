"use client";

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
 * Styled native {@code <select>}. Operates on string values; callers stringify
 * numeric ids (e.g. assignee id → String(id)).
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
      <span className="mb-1 block font-medium text-slate-600">{label}</span>
      <select
        className="w-full rounded border border-slate-300 bg-white px-2 py-1.5 text-sm disabled:text-slate-400"
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
    </label>
  );
}