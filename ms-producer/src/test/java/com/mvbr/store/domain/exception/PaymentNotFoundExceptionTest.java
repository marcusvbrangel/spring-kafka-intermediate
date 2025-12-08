package com.mvbr.store.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentNotFoundException - Unit Tests")
class PaymentNotFoundExceptionTest {

    @Test
    @DisplayName("Should create exception with paymentId")
    void shouldCreateExceptionWithPaymentId() {
        // Arrange
        String paymentId = "pay_123";

        // Act
        PaymentNotFoundException exception = new PaymentNotFoundException(paymentId);

        // Assert
        assertNotNull(exception);
        assertEquals(paymentId, exception.getPaymentId());
        assertEquals("Payment not found: pay_123", exception.getMessage());
    }

    @Test
    @DisplayName("Should create exception with different paymentId")
    void shouldCreateExceptionWithDifferentPaymentId() {
        // Arrange
        String paymentId = "pay_999";

        // Act
        PaymentNotFoundException exception = new PaymentNotFoundException(paymentId);

        // Assert
        assertEquals(paymentId, exception.getPaymentId());
        assertEquals("Payment not found: pay_999", exception.getMessage());
    }

    @Test
    @DisplayName("Should be a RuntimeException")
    void shouldBeRuntimeException() {
        // Arrange & Act
        PaymentNotFoundException exception = new PaymentNotFoundException("pay_1");

        // Assert
        assertTrue(exception instanceof RuntimeException);
    }
}
