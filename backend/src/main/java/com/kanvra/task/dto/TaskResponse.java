package com.kanvra.task.dto;

import com.kanvra.task.model.Task;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Task representation returned by the Task API (docs/SPEC.md §7). For create
 * and move, {@code eventId} carries the outbox event id so the frontend can
 * reconcile the corresponding WebSocket broadcast (SPEC §15.3 local echo).
 * {@code labelIds} mirrors the task's current labels so a version-gated PATCH
 * can round-trip them without wiping (Sprint 4).
 */
public record TaskResponse(
        Long id,
        Long columnId,
        String title,
        String description,
        String priority,
        Long assigneeId,
        LocalDate dueDate,
        List<Long> labelIds,
        Integer position,
        Integer version,
        Instant createdAt,
        java.util.UUID eventId) {

    /** Conflict/exception path: labels not loaded, empty list. */
    public static TaskResponse from(Task task) {
        return from(task, List.of());
    }

    public static TaskResponse from(Task task, List<Long> labelIds) {
        return new TaskResponse(task.getId(), task.getColumnId(), task.getTitle(), task.getDescription(),
                task.getPriority(), task.getAssigneeId(), task.getDueDate(),
                labelIds == null ? List.of() : List.copyOf(labelIds),
                task.getPosition(), task.getVersion(), task.getCreatedAt(), null);
    }

    public TaskResponse withEventId(java.util.UUID eventId) {
        return new TaskResponse(id, columnId, title, description, priority, assigneeId, dueDate,
                labelIds, position, version, createdAt, eventId);
    }
}