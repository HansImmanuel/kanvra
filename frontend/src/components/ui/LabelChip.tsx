"use client";

/**
 * Colored pill for a project label. Colors arrive as hex (`#RRGGBB`) from the
 * backend, so the chip renders with inline styles: a translucent tint fill, the
 * label color as text, and a subtle border.
 */
export default function LabelChip({ name, color }: { name: string; color: string }) {
  return (
    <span
      className="inline-block rounded px-1.5 py-0.5 text-xs font-medium"
      style={{
        backgroundColor: `${color}22`,
        color,
        border: `1px solid ${color}55`
      }}
    >
      {name}
    </span>
  );
}