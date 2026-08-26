import { redirect } from "next/navigation";

/**
 * The legacy project URL (/projects/{projectId}) now redirects to the default
 * project page — the Kanban Board. Kept so bookmarks and notification
 * deep-links continue to work.
 */
export default async function ProjectLegacyPage({
  params
}: {
  params: Promise<{ projectId: string }>;
}) {
  const { projectId } = await params;
  redirect(`/projects/${projectId}/board`);
}