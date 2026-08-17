package com.kanvra.common.error;

public class DuplicateEmailException extends ApiException {

    public DuplicateEmailException(String message) {
        super(409, "DUPLICATE_EMAIL", message);
    }
}
