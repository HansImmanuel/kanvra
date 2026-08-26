"use client";

import { use } from "react";
import AnalyticsPanel from "@/components/analytics/AnalyticsPanel";

/**
 * Project Analytics page (docs/SPEC.md §12.5). The Analytics panel now lives on
 * its own route instead of underneath the Kanban board; the backend endpoint and
 * the analytics pipeline are unchanged.
 */
export default function ProjectAnalyticsPage({
  params
}: {
  params: Promise<{ projectId: string }>;
}) {
  const { projectId } = use(params);
  const pid = Number(projectId);

  return (
    <div className="max-w-3xl">
      <AnalyticsPanel projectId={pid} />
    </div>
  );
}