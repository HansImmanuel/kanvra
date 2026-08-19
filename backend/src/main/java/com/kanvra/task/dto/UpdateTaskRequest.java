package com.kanvra.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * PATCH /api/v1/tasks/{taskId} body (docs/SPEC.md §7). {@code version} is
 * required and checked against the stored row (optimistic concurrency).
 */
public record UpdateTaskRequest(
        @NotNull @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @Size(max = 20) String priority,
        Long assigneeId,
        LocalDate dueDate,
        List<Long> labelIds,
        @NotNull Integer version) {
}