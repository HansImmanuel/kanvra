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

    @Test
    void optimisticLockExceptionCarriesCurrentState() {
        Task current = new Task();
        org.springframework.test.util.ReflectionTestUtils.setField(current, "id", 7L);
        current.setTitle("Server current");
        current.setVersion(5);

        ResponseEntity<ApiError> response = handler.handleOptimisticConflict(
                new OptimisticLockException("stale version",
                        com.kanvra.task.dto.TaskResponse.from(current)));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("TASK_VERSION_CONFLICT");
        // The conflict body must expose the server's current task (SPEC.md §7.2).
        assertThat(response.getBody().currentState()).isEqualTo(com.kanvra.task.dto.TaskResponse.from(current));
    }
}