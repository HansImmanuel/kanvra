package com.kanvra.common.error;

/**
 * 409 optimistic-lock conflict (stale task {@code version}).
 */
public class OptimisticLockException extends ApiException {

    public OptimisticLockException(String message) {
        super(409, "TASK_VERSION_CONFLICT", message);
    }
}
