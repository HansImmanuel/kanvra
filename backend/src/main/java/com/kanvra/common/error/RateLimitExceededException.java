package com.kanvra.common.error;

public class RateLimitExceededException extends ApiException {

    public RateLimitExceededException(String message) {
        super(429, "TOO_MANY_REQUESTS", message);
    }
}
