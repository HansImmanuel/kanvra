package com.kanvra.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * POST /api/v1/tasks/{taskId}/move body (docs/SPEC.md §7). Used for both
 * cross-column moves and same-column reorders (pass the current columnId).
 */
public record MoveTaskRequest(
        @NotNull Long targetColumnId,
        @NotNull @PositiveOrZero Integer position,
        @NotNull Integer version) {
}