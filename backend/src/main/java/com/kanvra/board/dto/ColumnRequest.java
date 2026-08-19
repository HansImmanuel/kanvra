package com.kanvra.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST/PATCH /api/v1/boards/{boardId}/columns body (docs/SPEC.md §6).
 */
public record ColumnRequest(
        @NotBlank @Size(max = 100) String name) {
}
