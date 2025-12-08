package com.mvbr.store.infrastructure.adapter.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardized error response structure for API errors.
 *
 * This DTO provides a consistent format for all error responses,
 * making it easier for clients to parse and handle errors.
 *
 * @param timestamp    When the error occurred (ISO-8601 format)
 * @param status       HTTP status code (400, 404, 500, etc.)
 * @param error        HTTP status text ("Bad Request", "Not Found", etc.)
 * @param message      High-level error message for developers
 * @param path         Request path that caused the error
 * @param errors       List of detailed field-level validation errors (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> errors
) {
    /**
     * Constructor for simple errors without field-level details.
     */
    public ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}
