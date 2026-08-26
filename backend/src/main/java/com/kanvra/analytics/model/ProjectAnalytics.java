package com.kanvra.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Per-project analytics counters (docs/SPEC.md §12.5), accumulated by the
 * Analytics Consumer (group {@code kanvra-analytics}) from domain events.
 * Keyed by the project id — exactly one row per project.
 *
 * <p>Updates are a plain read-modify-write like the rest of the codebase; with
 * the single-partition MVP topic and one active consumer per group, per-project
 * events are effectively serialized, so lost updates cannot occur in practice.
 * Duplicate deliveries are guarded by the {@code analytics_events.event_id}
 * ledger written in the same transaction (docs/SPEC.md §12.5).
 */
@Entity
@Table(name = "project_analytics")
public class ProjectAnalytics {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "tasks_created", nullable = false)
    private long tasksCreated;

    @Column(name = "tasks_completed", nullable = false)
    private long tasksCompleted;

    @Column(name = "tasks_moved", nullable = false)
    private long tasksMoved;

    @Column(name = "tasks_deleted", nullable = false)
    private long tasksDeleted;

    @Column(name = "comments_created", nullable = false)
    private long commentsCreated;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void incrementTasksCreated() {
        this.tasksCreated++;
    }

    public void incrementTasksCompleted() {
        this.tasksCompleted++;
    }

    public void incrementTasksMoved() {
        this.tasksMoved++;
    }

    public void incrementTasksDeleted() {
        this.tasksDeleted++;
    }

    public void incrementCommentsCreated() {
        this.commentsCreated++;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public long getTasksCreated() {
        return tasksCreated;
    }

    public long getTasksCompleted() {
        return tasksCompleted;
    }

    public long getTasksMoved() {
        return tasksMoved;
    }

    public long getTasksDeleted() {
        return tasksDeleted;
    }

    public long getCommentsCreated() {
        return commentsCreated;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}