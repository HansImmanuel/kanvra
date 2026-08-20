package com.kanvra.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/tasks/{taskId}/comments body (docs/SPEC.md §8).
 */
public record CreateCommentRequest(
        @NotBlank @Size(max = 2000) String content) {
}