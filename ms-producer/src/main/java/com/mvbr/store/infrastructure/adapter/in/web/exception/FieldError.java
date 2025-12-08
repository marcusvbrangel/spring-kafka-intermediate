package com.mvbr.store.infrastructure.adapter.in.web.exception;

/**
 * Represents a field-level validation error.
 *
 * Used in ErrorResponse to provide detailed information about
 * which fields failed validation and why.
 *
 * @param field         Name of the field that failed validation (e.g., "paymentId")
 * @param message       Human-readable error message
 * @param rejectedValue The value that was rejected (optional, for debugging)
 */
public record FieldError(
        String field,
        String message,
        Object rejectedValue
) {
    /**
     * Constructor without rejected value (for security reasons, hide sensitive data).
     */
    public FieldError(String field, String message) {
        this(field, message, null);
    }
}
