package com.kanvra.task.dto;

import com.kanvra.task.model.Task;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Task representation returned by the Task API (docs/SPEC.md §7). For create
 * and move, {@code eventId} carries the outbox event id so the frontend can
 * reconcile the corresponding WebSocket broadcast (SPEC §15.3 local echo).
 */
public record TaskResponse(
        Long id,
        Long columnId,
        String title,
        String description,
        String priority,
        Long assigneeId,
        LocalDate dueDate,
        Integer position,
        Integer version,
        Instant createdAt,
        java.util.UUID eventId) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getColumnId(), task.getTitle(), task.getDescription(),
                task.getPriority(), task.getAssigneeId(), task.getDueDate(), task.getPosition(),
                task.getVersion(), task.getCreatedAt(), null);
    }

    public TaskResponse withEventId(java.util.UUID eventId) {
        return new TaskResponse(id, columnId, title, description, priority, assigneeId, dueDate,
                position, version, createdAt, eventId);
    }
}