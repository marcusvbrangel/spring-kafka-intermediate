package com.mvbr.store.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

/**
 * DTO for Payment HTTP Response (Web Adapter Output).
 *
 * This is the INFRASTRUCTURE layer - represents HTTP/JSON structure.
 * Decouples REST API contract from domain/application layers.
 */
public record PaymentResponseDto(
        String paymentId,
        String userId,
        BigDecimal amount,
        String currency,
        String status,
        long createdAt
) {}
