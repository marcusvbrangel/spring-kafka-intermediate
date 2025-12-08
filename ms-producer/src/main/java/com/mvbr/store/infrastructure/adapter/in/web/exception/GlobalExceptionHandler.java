package com.mvbr.store.infrastructure.adapter.in.web.exception;

import com.mvbr.store.domain.exception.InvalidPaymentException;
import com.mvbr.store.domain.exception.PaymentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Global exception handler for all REST API errors.
 *
 * This @RestControllerAdvice captures exceptions thrown by controllers
 * and converts them into standardized JSON error responses.
 *
 * HANDLES:
 * - Bean Validation errors (MethodArgumentNotValidException)
 * - Domain exceptions (InvalidPaymentException, PaymentNotFoundException)
 * - Malformed JSON (HttpMessageNotReadableException)
 * - Type mismatch errors (MethodArgumentTypeMismatchException)
 * - Unexpected errors (Exception)
 *
 * BENEFITS:
 * - Consistent error format across all endpoints
 * - Automatic HTTP status mapping
 * - Detailed field-level validation errors
 * - Centralized error logging
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // =============================================================================================
    // 1. BEAN VALIDATION ERRORS (HTTP 400)
    // =============================================================================================

    /**
     * Handles Jakarta Bean Validation errors (e.g., @NotNull, @Size, @Pattern violations).
     *
     * Triggered when @Valid fails on a @RequestBody parameter.
     *
     * Returns HTTP 400 with detailed field-level errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> fieldErrors = new ArrayList<>();

        // Extract all field-level validation errors
        bindingResult.getFieldErrors().forEach(error -> {
            fieldErrors.add(new FieldError(
                    error.getField(),
                    error.getDefaultMessage(),
                    error.getRejectedValue()
            ));
        });

        // Extract global validation errors (if any)
        bindingResult.getGlobalErrors().forEach(error -> {
            fieldErrors.add(new FieldError(
                    error.getObjectName(),
                    error.getDefaultMessage()
            ));
        });

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields. Please check the 'errors' field for details.",
                request.getRequestURI(),
                fieldErrors
        );

        log.warn("Validation failed for request to {}: {} field errors",
                request.getRequestURI(), fieldErrors.size());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =============================================================================================
    // 2. MALFORMED JSON / UNREADABLE REQUEST BODY (HTTP 400)
    // =============================================================================================

    /**
     * Handles errors when the request body cannot be parsed (e.g., invalid JSON syntax).
     *
     * Examples:
     * - Missing quotes in JSON
     * - Invalid number format
     * - Wrong data type (string instead of number)
     *
     * Returns HTTP 400 with a clear message.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        String detailedMessage = ex.getMostSpecificCause().getMessage();

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Malformed JSON request: " + detailedMessage,
                request.getRequestURI()
        );

        log.warn("Malformed JSON in request to {}: {}", request.getRequestURI(), detailedMessage);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =============================================================================================
    // 3. TYPE MISMATCH ERRORS (HTTP 400)
    // =============================================================================================

    /**
     * Handles errors when a request parameter has the wrong type.
     *
     * Example: Sending "abc" for a parameter that expects an integer.
     *
     * Returns HTTP 400 with a clear message.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String parameterName = ex.getName();
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        Object rejectedValue = ex.getValue();

        String message = String.format(
                "Parameter '%s' should be of type '%s' but received value: '%s'",
                parameterName, requiredType, rejectedValue
        );

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        log.warn("Type mismatch in request to {}: {}", request.getRequestURI(), message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // =============================================================================================
    // 4. DOMAIN EXCEPTIONS - BUSINESS RULE VIOLATIONS (HTTP 422)
    // =============================================================================================

    /**
     * Handles domain-level validation errors (business rule violations).
     *
     * Example: Trying to approve a payment that's already canceled.
     *
     * Returns HTTP 422 (Unprocessable Entity) - semantically correct but business rule violation.
     */
    @ExceptionHandler(InvalidPaymentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentException(
            InvalidPaymentException ex,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );

        log.warn("Invalid payment operation in request to {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    // =============================================================================================
    // 5. RESOURCE NOT FOUND (HTTP 404)
    // =============================================================================================

    /**
     * Handles cases where a requested resource doesn't exist.
     *
     * Example: Trying to fetch a payment with ID that doesn't exist.
     *
     * Returns HTTP 404 (Not Found).
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFoundException(
            PaymentNotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );

        log.warn("Payment not found in request to {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    // =============================================================================================
    // 6. UNEXPECTED ERRORS (HTTP 500)
    // =============================================================================================

    /**
     * Catches all unexpected exceptions that weren't handled by specific handlers.
     *
     * This is a safety net to prevent stack traces from leaking to clients.
     *
     * Returns HTTP 500 (Internal Server Error) with a generic message.
     * Full exception details are logged for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError(
            Exception ex,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please contact support if the problem persists.",
                request.getRequestURI()
        );

        // Log full stack trace for debugging (but don't expose to client)
        log.error("Unexpected error in request to {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
