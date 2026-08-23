package com.kanvra.common.error;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Global exception handler producing the consistent JSON error shape from
 * docs/SPEC.md §17.1. Field-level {@code errors[]} is emitted only for
 * {@code VALIDATION_ERROR}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ValidationException ex) {
        ApiError error = new ApiError(
                java.time.OffsetDateTime.now(),
                422,
                "VALIDATION_ERROR",
                ex.getMessage(),
                ex.getErrors());
        return ResponseEntity.status(422).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ValidationFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ValidationFieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError error = new ApiError(
                java.time.OffsetDateTime.now(),
                422,
                "VALIDATION_ERROR",
                "Request validation failed",
                errors);
        return ResponseEntity.status(422).body(error);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ApiError> handleOptimisticConflict(OptimisticLockException ex) {
        // SPEC.md §7.2: a version conflict must return the server's current task
        // state so the client can re-render instead of staying stuck on a stale version.
        ApiError error = new ApiError(
                java.time.OffsetDateTime.now(),
                409,
                "TASK_VERSION_CONFLICT",
                ex.getMessage(),
                null,
                ex.getCurrentState());
        return ResponseEntity.status(409).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "RESOURCE_IN_USE", "Resource is in use and cannot be modified"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Lost update detected ({})", ex.getIdentifier());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "TASK_VERSION_CONFLICT",
                        "Task was modified by someone else; refresh and retry"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "BAD_REQUEST", "Malformed request body"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR", "Unexpected server error"));
    }
}
