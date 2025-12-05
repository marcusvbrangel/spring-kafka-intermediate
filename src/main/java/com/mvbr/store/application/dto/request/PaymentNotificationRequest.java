package com.mvbr.store.application.dto.request;

public record PaymentNotificationRequest(
        String paymentId,
        String userId,
        double amount,
        String message
) {}