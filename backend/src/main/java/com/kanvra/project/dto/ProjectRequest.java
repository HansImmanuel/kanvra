package com.kanvra.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/projects body (docs/SPEC.md §4).
 */
public record ProjectRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description) {
}
