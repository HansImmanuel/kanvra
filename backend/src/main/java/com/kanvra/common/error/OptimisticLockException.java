package com.kanvra.common.error;

/**
 * 409 optimistic-lock conflict (stale task {@code version}).
 *
 * <p>{@code currentState} carries the server's current task representation so
 * the response body can include it for client re-rendering (SPEC.md §7.2).
 */
public class OptimisticLockException extends ApiException {

    private final Object currentState;

    public OptimisticLockException(String message) {
        this(message, null);
    }

    public OptimisticLockException(String message, Object currentState) {
        super(409, "TASK_VERSION_CONFLICT", message);
        this.currentState = currentState;
    }

    public Object getCurrentState() {
        return currentState;
    }
}
