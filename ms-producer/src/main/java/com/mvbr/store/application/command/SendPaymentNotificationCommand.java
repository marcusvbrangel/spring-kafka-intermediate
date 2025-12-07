package com.mvbr.store.application.command;

/**
 * Command for sending payment notification (Inbound - Use Case Input).
 *
 * Replaces PaymentNotificationRequest in Hexagonal Architecture.
 * Commands represent user intentions and are part of the application layer.
 */
public record SendPaymentNotificationCommand(
        String paymentId,
        String userId,
        double amount,
        String message
) {}
