package com.mvbr.store.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

/**
 * DTO for Payment Approved HTTP Request (Web Adapter Input).
 *
 * This is the INFRASTRUCTURE layer - represents HTTP/JSON structure.
 * Decouples REST API contract from domain/application layers.
 *
 * SAME STRUCTURE as original PaymentApprovedRequest.
 */
public record PaymentApprovedRequestDto(
        String paymentId,
        String userId,
        BigDecimal amount,
        String currency
) {}
