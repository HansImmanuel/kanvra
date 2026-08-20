package com.kanvra.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/comments/{commentId} body (docs/SPEC.md §8). Only the comment
 * author may edit; no time limit on edits.
 */
public record UpdateCommentRequest(
        @NotBlank @Size(max = 2000) String content) {
}