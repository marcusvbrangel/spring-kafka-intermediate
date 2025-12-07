package com.mvbr.store.domain.exception;

/**
 * Domain Exception - Invalid Payment.
 *
 * Thrown when payment data violates business rules.
 * This is a DOMAIN exception - part of the business rules.
 */
public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }

    public InvalidPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
