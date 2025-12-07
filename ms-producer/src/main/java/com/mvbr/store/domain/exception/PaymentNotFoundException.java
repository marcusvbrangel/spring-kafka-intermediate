package com.mvbr.store.domain.exception;

/**
 * Domain Exception - Payment Not Found.
 *
 * Thrown when trying to access a payment that doesn't exist.
 * This is a DOMAIN exception - part of the business rules.
 */
public class PaymentNotFoundException extends RuntimeException {

    private final String paymentId;

    public PaymentNotFoundException(String paymentId) {
        super("Payment not found: " + paymentId);
        this.paymentId = paymentId;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
