import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ProjectSettingsPanel from "./ProjectSettingsPanel";
import * as actions from "@/lib/actions";
import { currentUserSafe } from "@/lib/auth";

vi.mock("@/lib/actions", () => ({
  listMembers: vi.fn(),
  addMember: vi.fn(),
  removeMember: vi.fn(),
  listProjectLabels: vi.fn(),
  createLabel: vi.fn(),
  updateLabel: vi.fn(),
  deleteLabel: vi.fn()
}));
vi.mock("@/lib/auth", () => ({
  currentUserSafe: vi.fn()
}));

const project = {
  id: 10,
  name: "Alpha",
  description: null,
  status: "ACTIVE",
  ownerId: 1,
  createdAt: "2026-01-01T00:00:00Z"
};

const members = [
  { id: 1, name: "Owner Olive", role: "OWNER", joinedAt: "2026-01-01T00:00:00Z" },
  { id: 9, name: "Member Max", role: "MEMBER", joinedAt: "2026-01-02T00:00:00Z" }
];

const labels = [
  { id: 21, projectId: 10, name: "bug", color: "#DC2626" },
  { id: 22, projectId: 10, name: "api", color: "#2563EB" }
];

function setup() {
  return render(<ProjectSettingsPanel project={project} onClose={() => {}} />);
}

describe("ProjectSettingsPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(actions.listMembers).mockResolvedValue({
      content: members,
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 1
    });
    vi.mocked(actions.listProjectLabels).mockResolvedValue(labels);
  });

  it("renders members and labels but hides OWNER controls from a MEMBER", async () => {
    vi.mocked(currentUserSafe).mockResolvedValue({
      id: 9,
      name: "Member Max",
      email: "max@example.com",
      avatarUrl: null
    });

    setup();

    expect(await screen.findByText("Owner Olive")).toBeInTheDocument();
    expect(screen.getByText("bug")).toBeInTheDocument();
    expect(screen.getByText("api")).toBeInTheDocument();

    // MEMBER must not see member-management controls…
    expect(screen.queryByRole("button", { name: "Add member" })).toBeNull();
    expect(screen.queryByLabelText(/Remove /i)).toBeNull();
    // …but label CRUD is membership-level per SPEC §9.
    expect(screen.getByRole("button", { name: /Delete bug/i })).toBeInTheDocument();
  });

  it("as OWNER, removes a member and adds a new one", async () => {
    vi.mocked(currentUserSafe).mockResolvedValue({
      id: 1,
      name: "Owner Olive",
      email: "owner@example.com",
      avatarUrl: null
    });
    vi.mocked(actions.removeMember).mockResolvedValue(undefined as never);
    vi.mocked(actions.addMember).mockResolvedValue(memberAsAdded());

    setup();
    expect(await screen.findByText("Owner Olive")).toBeInTheDocument();

    // The owner row itself has no Remove control; the member row does.
    fireEvent.click(screen.getByLabelText("Remove Member Max"));
    await waitFor(() =>
      expect(actions.removeMember).toHaveBeenCalledWith(project.id, 9)
    );

    fireEvent.change(screen.getByLabelText("User id"), { target: { value: "12" } });
    fireEvent.click(screen.getByRole("button", { name: "Add member" }));
    await waitFor(() =>
      expect(actions.addMember).toHaveBeenCalledWith(project.id, 12, "MEMBER")
    );
  });

  it("creates a label through the composer with the picked color", async () => {
    vi.mocked(actions.createLabel).mockResolvedValue(labels[0]);
    setup();

    fireEvent.change(await screen.findByLabelText("New label name"), {
      target: { value: "chore" }
    });
    fireEvent.click(screen.getByRole("button", { name: "Use color #059669" }));
    fireEvent.click(screen.getByRole("button", { name: "Create label" }));

    await waitFor(() =>
      expect(actions.createLabel).toHaveBeenCalledWith(project.id, "chore", "#059669")
    );
  });
});

function memberAsAdded() {
  return { id: 12, name: "New", role: "MEMBER", joinedAt: "2026-01-03T00:00:00Z" };
}