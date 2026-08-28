import AnalyticsPanel from "@/components/analytics/AnalyticsPanel";

/**
 * Project Analytics page (docs/SPEC.md §12.5). Server Component: it only
 * unwraps the route param and hands it to the client analytics panel — no
 * client boundary needed at the page level (phase 10).
 */
export default async function ProjectAnalyticsPage({
  params
}: {
  params: Promise<{ projectId: string }>;
}) {
  const { projectId } = await params;
  const pid = Number(projectId);

  return (
    <div className="max-w-3xl">
      <AnalyticsPanel projectId={pid} />
    </div>
  );
}