package com.mvbr.store.application.command;

import com.mvbr.store.domain.model.PaymentStatus;

import java.math.BigDecimal;

/**
 * Response object for payment operations (Outbound - Use Case Output).
 *
 * Represents the result of payment use cases.
 * This is part of the application layer.
 */
public record PaymentResponse(
        String paymentId,
        String userId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        long createdAt
) {}
