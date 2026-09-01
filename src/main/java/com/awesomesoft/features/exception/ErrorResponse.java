package com.awesomesoft.features.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** Same error shape as the audit application: {timestamp, status, error, message, validationErrors}. */
public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message,
                            Map<String, String> validationErrors) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, null);
    }

    public static ErrorResponse validation(String message, Map<String, String> validationErrors) {
        return new ErrorResponse(LocalDateTime.now(), 400, "Validation Failed", message, validationErrors);
    }
}
