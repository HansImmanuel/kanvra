package com.kanvra.common.error;

/**
 * 404 resource-not-found. The {@code code} carries the specific resource
 * (e.g. {@code PROJECT_NOT_FOUND}, {@code TASK_NOT_FOUND}).
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String code, String message) {
        super(404, code, message);
    }
}
