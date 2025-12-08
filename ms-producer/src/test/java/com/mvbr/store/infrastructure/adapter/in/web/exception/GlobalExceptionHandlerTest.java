package com.mvbr.store.infrastructure.adapter.in.web.exception;

import com.mvbr.store.domain.exception.InvalidPaymentException;
import com.mvbr.store.domain.exception.PaymentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler - Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/payments/approved");
    }

    // =======================================
    //      VALIDATION ERRORS (HTTP 400)
    // =======================================

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException with field errors")
    void shouldHandleMethodArgumentNotValidException() throws Exception {
        // Arrange
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "paymentRequest");
        bindingResult.addError(new FieldError("paymentRequest", "paymentId",
            "invalid_id", false, null, null, "Payment ID must be alphanumeric"));
        bindingResult.addError(new FieldError("paymentRequest", "amount",
            "0", false, null, null, "Amount must be positive"));

        MethodParameter methodParameter = new MethodParameter(
            this.getClass().getDeclaredMethod("dummyMethod"), -1
        );
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
            methodParameter, bindingResult
        );

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationErrors(exception, request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(400, errorResponse.status());
        assertEquals("Bad Request", errorResponse.error());
        assertEquals("/api/payments/approved", errorResponse.path());
        assertNotNull(errorResponse.errors());
        assertEquals(2, errorResponse.errors().size());

        // Verify field errors
        assertTrue(errorResponse.errors().stream()
            .anyMatch(e -> e.field().equals("paymentId")));
        assertTrue(errorResponse.errors().stream()
            .anyMatch(e -> e.field().equals("amount")));
    }

    // =======================================
    //      MALFORMED JSON (HTTP 400)
    // =======================================

    @Test
    @DisplayName("Should handle HttpMessageNotReadableException")
    void shouldHandleHttpMessageNotReadableException() {
        // Arrange
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
            "Invalid JSON", new RuntimeException("Unexpected character")
        );

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMalformedJson(exception, request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(400, errorResponse.status());
        assertEquals("Bad Request", errorResponse.error());
        assertTrue(errorResponse.message().contains("Malformed JSON"));
        assertEquals("/api/payments/approved", errorResponse.path());
    }

    // =======================================
    //      TYPE MISMATCH (HTTP 400)
    // =======================================

    @Test
    @DisplayName("Should handle MethodArgumentTypeMismatchException")
    void shouldHandleMethodArgumentTypeMismatchException() throws Exception {
        // Arrange
        MethodParameter methodParameter = new MethodParameter(
            this.getClass().getDeclaredMethod("dummyMethod"), -1
        );
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
            "abc", Integer.class, "amount", methodParameter, new RuntimeException()
        );

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTypeMismatch(exception, request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(400, errorResponse.status());
        assertEquals("Bad Request", errorResponse.error());
        assertTrue(errorResponse.message().contains("amount"));
        assertTrue(errorResponse.message().contains("Integer"));
        assertEquals("/api/payments/approved", errorResponse.path());
    }

    // =======================================
    //      DOMAIN EXCEPTIONS (HTTP 422)
    // =======================================

    @Test
    @DisplayName("Should handle InvalidPaymentException with HTTP 422")
    void shouldHandleInvalidPaymentException() {
        // Arrange
        InvalidPaymentException exception = new InvalidPaymentException(
            "Cannot approve a canceled payment"
        );

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidPaymentException(exception, request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());

        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(422, errorResponse.status());
        assertEquals("Unprocessable Entity", errorResponse.error());
        assertEquals("Cannot approve a canceled payment", errorResponse.message());
        assertEquals("/api/payments/approved", errorResponse.path());
    }

    // =======================================
    //      RESOURCE NOT FOUND (HTTP 404)
    // =======================================

    @Test
    @DisplayName("Should handle PaymentNotFoundException with HTTP 404")
    void shouldHandlePaymentNotFoundException() {
        // Arrange
        PaymentNotFoundException exception = new PaymentNotFoundException("pay_123");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handlePaymentNotFoundException(exception, request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(404, errorResponse.status());
        assertEquals("Not Found", errorResponse.error());
        assertTrue(errorResponse.message().contains("pay_123"));
        assertEquals("/api/payments/approved", errorResponse.path());
    }

    // =======================================
    //      UNEXPECTED ERRORS (HTTP 500)
    // =======================================

    @Test
    @DisplayName("Should handle unexpected Exception with HTTP 500")
    void shouldHandleUnexpectedException() {
        // Arrange
        Exception exception = new RuntimeException("Unexpected database error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnexpectedError(exception, request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(500, errorResponse.status());
        assertEquals("Internal Server Error", errorResponse.error());
        assertTrue(errorResponse.message().contains("unexpected error occurred"));
        assertEquals("/api/payments/approved", errorResponse.path());
    }

    @Test
    @DisplayName("Should handle NullPointerException with HTTP 500")
    void shouldHandleNullPointerException() {
        // Arrange
        NullPointerException exception = new NullPointerException("Null value encountered");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnexpectedError(exception, request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(500, errorResponse.status());
        // Should not expose internal error details to client
        assertFalse(errorResponse.message().contains("Null value encountered"));
        assertTrue(errorResponse.message().contains("unexpected error"));
    }

    // =======================================
    //      HELPER METHOD
    // =======================================

    @SuppressWarnings("unused")
    private void dummyMethod() {
        // Used for creating MethodParameter instances in tests
    }
}
