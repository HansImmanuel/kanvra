package com.kanvra.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standard JSON error response (docs/SPEC.md §17.1).
 *
 * <p>{@code errors} is only populated for {@code VALIDATION_ERROR} responses.
 * {@code currentState} is only populated for {@code TASK_VERSION_CONFLICT}
 * (created via {@link OptimisticLockException}) so the client can re-render
 * from the server's current task without a follow-up GET (SPEC.md §7.2).
 * Both are omitted when null ({@code @JsonInclude(NON_NULL)}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        List<ValidationFieldError> errors,
        Object currentState) {

    /** Convenience constructor for errors without a {@code currentState}. */
    public ApiError(OffsetDateTime timestamp, int status, String code, String message,
                    List<ValidationFieldError> errors) {
        this(timestamp, status, code, message, errors, null);
    }

    public static ApiError of(int status, String code, String message) {
        return new ApiError(OffsetDateTime.now(), status, code, message, null, null);
    }
}
