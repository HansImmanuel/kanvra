package com.kanvra.common.error;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard JSON error response (docs/SPEC.md §17.1).
 *
 * {@code errors} is only populated for {@code VALIDATION_ERROR} responses.
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        List<ValidationFieldError> errors) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(OffsetDateTime.now(), status, code, message, null);
    }
}
