package com.kanvra.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/auth/login body (SPEC.md §3.3).
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password) {
}
