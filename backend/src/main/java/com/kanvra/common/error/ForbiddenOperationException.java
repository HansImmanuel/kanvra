package com.kanvra.common.error;

public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(403, "FORBIDDEN", message);
    }
}
