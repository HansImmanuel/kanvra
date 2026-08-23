import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import TaskCard from "./TaskCard";

describe("TaskCard", () => {
  const baseTask = {
    id: 7,
    title: "Fix auth",
    priority: "HIGH",
    position: 0,
    version: 2,
    assignee: null,
    labels: [],
    dueDate: null,
    commentCount: 0
  };

  it("renders title, priority, labels and the version", () => {
    const task = {
      ...baseTask,
      labels: [{ id: 1, name: "backend", color: "#2563EB" }],
      commentCount: 3
    };
    render(<TaskCard task={task} />);

    expect(screen.getByText("Fix auth")).toBeInTheDocument();
    expect(screen.getByText("HIGH")).toBeInTheDocument();
    expect(screen.getByText("backend")).toBeInTheDocument();
    expect(screen.getByText(/3/)).toBeInTheDocument();
    expect(screen.getByText(/v2/)).toBeInTheDocument();
  });

  it("does not show the comment-count badge when there are no comments", () => {
    render(<TaskCard task={baseTask} />);
    expect(screen.queryByText(/💬/)).toBeNull();
  });
});