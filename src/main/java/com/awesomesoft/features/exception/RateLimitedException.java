package com.awesomesoft.features.exception;

/** Thrown when the login rate limit is exceeded; mapped to HTTP 429. */
public class RateLimitedException extends RuntimeException {

    public RateLimitedException(String message) {
        super(message);
    }
}
