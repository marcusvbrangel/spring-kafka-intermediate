package com.mvbr.store.infrastructure.adapter.in.web.dto;

/**
 * DTO for Payment Notification HTTP Request (Web Adapter Input).
 *
 * This is the INFRASTRUCTURE layer - represents HTTP/JSON structure.
 * Decouples REST API contract from domain/application layers.
 *
 * SAME STRUCTURE as original PaymentNotificationRequest.
 */
public record PaymentNotificationRequestDto(
        String paymentId,
        String userId,
        double amount,
        String message
) {}
