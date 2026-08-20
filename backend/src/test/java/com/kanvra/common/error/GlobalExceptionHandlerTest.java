package com.kanvra.common.error;

import com.kanvra.task.model.Task;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies GlobalExceptionHandler maps DB constraint / optimistic-lock failures
 * to stable, retryable HTTP statuses instead of letting them fall through to
 * the generic 500 handler (docs/SPEC.md §17.1).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void dataIntegrityViolationMapsTo409ResourceInUse() {
        ResponseEntity<ApiError> response =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("fk violation"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_IN_USE");
    }

    @Test
    void optimisticLockMapsTo409TaskVersionConflict() {
        ResponseEntity<ApiError> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(Task.class, 7L));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("TASK_VERSION_CONFLICT");
    }
}