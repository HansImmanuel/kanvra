package com.kanvra.common.error;

import java.util.List;

/**
 * 422 business-validation failure with field-level details.
 */
public class ValidationException extends ApiException {

    private final List<ValidationFieldError> errors;

    public ValidationException(List<ValidationFieldError> errors) {
        super(422, "VALIDATION_ERROR", "Request validation failed");
        this.errors = errors;
    }

    public ValidationException(String message, List<ValidationFieldError> errors) {
        super(422, "VALIDATION_ERROR", message);
        this.errors = errors;
    }

    public List<ValidationFieldError> getErrors() {
        return errors;
    }
}
