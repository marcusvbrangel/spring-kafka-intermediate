package com.mvbr.store.event;

public record PaymentNotificationEvent(
        String eventId,
        String paymentId,
        String userId,
        double amount,
        String message,
        long timestamp
) {}