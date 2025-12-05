package com.mvbr.store.infrastructure.messaging.event;

public record PaymentNotificationEvent(
        String eventId,
        String paymentId,
        String userId,
        double amount,
        String message,
        long timestamp
) {}