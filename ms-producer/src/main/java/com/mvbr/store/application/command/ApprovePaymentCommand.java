package com.mvbr.store.application.command;

import java.math.BigDecimal;

/**
 * Command for approving a payment (Inbound - Use Case Input).
 *
 * Replaces PaymentApprovedRequest in Hexagonal Architecture.
 * Commands represent user intentions and are part of the application layer.
 */
public record ApprovePaymentCommand(
        String paymentId,
        String userId,
        BigDecimal amount,
        String currency
) {}
