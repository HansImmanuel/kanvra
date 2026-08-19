package com.kanvra.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * POST /api/v1/columns/{columnId}/tasks body (docs/SPEC.md §7).
 */
public record CreateTaskRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @Size(max = 20) String priority,
        Long assigneeId,
        LocalDate dueDate,
        List<Long> labelIds) {
}