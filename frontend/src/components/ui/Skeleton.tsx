/**
 * Pulsing placeholder block; the shape comes entirely from className so each
 * surface can approximate its final content. Pulse is disabled for users who
 * prefer reduced motion.
 */
export default function Skeleton({
  tone = "elevated",
  className = ""
}: {
  /** `edge` renders a slightly stronger fill for secondary lines. */
  tone?: "elevated" | "edge";
  className?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={`block animate-pulse rounded ${
        tone === "edge" ? "bg-edge" : "bg-elevated"
      } motion-reduce:animate-none ${className}`.trim()}
    />
  );
}
