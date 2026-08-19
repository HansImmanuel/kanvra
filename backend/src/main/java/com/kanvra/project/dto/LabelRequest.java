package com.kanvra.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST/PATCH label body (docs/SPEC.md §9). {@code color} is a 7-character hex
 * string ({@code #RRGGBB}).
 */
public record LabelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "color must be a 7-character hex string like #2563EB")
        String color) {
}
