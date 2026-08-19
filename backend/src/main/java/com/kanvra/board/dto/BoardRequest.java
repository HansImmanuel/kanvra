package com.kanvra.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST/PATCH /api/v1/projects/{projectId}/boards body (docs/SPEC.md §5).
 */
public record BoardRequest(
        @NotBlank @Size(max = 100) String name) {
}
