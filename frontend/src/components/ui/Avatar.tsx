"use client";

/**
 * Avatar circle. When an {@code avatarUrl} is present it renders the image;
 * otherwise it falls back to up to two initials from the name.
 */
export default function Avatar({
  name,
  avatarUrl,
  size = 24
}: {
  name: string;
  avatarUrl?: string | null;
  size?: number;
}) {
  const initials = (
    (name ?? "?")
      .split(/\s+/)
      .map((part) => part[0])
      .filter(Boolean)
      .slice(0, 2)
      .join("")
      .toUpperCase() || "?"
  );

  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={`${name}'s avatar`}
        className="rounded-full object-cover"
        style={{ width: size, height: size }}
      />
    );
  }
  return (
    <span
      aria-hidden="true"
      className="inline-flex items-center justify-center rounded-full bg-slate-300 text-slate-700 font-medium select-none"
      style={{ width: size, height: size, fontSize: Math.max(10, Math.round(size * 0.38)) }}
    >
      {initials}
    </span>
  );
}