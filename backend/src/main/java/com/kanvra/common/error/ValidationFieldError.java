package com.kanvra.common.error;

import java.time.OffsetDateTime;

/**
 * A single field-level validation message, present only in
 * {@code VALIDATION_ERROR} responses (docs/SPEC.md §17.1).
 */
public record ValidationFieldError(String field, String message) {
}
