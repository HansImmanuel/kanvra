package com.kanvra.common.error;

/**
 * Base class for domain/business exceptions that are mapped to a stable
 * JSON error response with a machine-readable {@code code}.
 */
public abstract class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    protected ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
