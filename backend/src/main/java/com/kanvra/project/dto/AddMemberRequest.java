package com.kanvra.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/v1/projects/{projectId}/members body (docs/SPEC.md §4). MVP roles
 * are OWNER and MEMBER.
 */
public record AddMemberRequest(
        @NotNull Long userId,
        @NotNull @Pattern(regexp = "OWNER|MEMBER", message = "role must be OWNER or MEMBER") String role) {
}
