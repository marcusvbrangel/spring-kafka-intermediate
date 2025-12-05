package com.mvbr.store.dto;

public record PaymentNotificationRequest(
        String paymentId,
        String userId,
        double amount,
        String message
) {}