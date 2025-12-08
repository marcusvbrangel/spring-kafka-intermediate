package com.mvbr.store.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO for Payment Approved HTTP Request (Web Adapter Input).
 *
 * This is the INFRASTRUCTURE layer - represents HTTP/JSON structure.
 * Decouples REST API contract from domain/application layers.
 *
 * SAME STRUCTURE as original PaymentApprovedRequest.
 *
 * VALIDATIONS:
 * - All fields are required (not null, not blank)
 * - PaymentId: alphanumeric, 1-100 characters
 * - UserId: alphanumeric, 1-100 characters
 * - Amount: positive, max 10 digits before decimal, 2 after, max 1 billion
 * - Currency: ISO 4217 format (3 uppercase letters: BRL, USD, EUR, etc.)
 */
public record PaymentApprovedRequestDto(

        /**
         * Payment unique identifier.
         * Must be alphanumeric, between 1 and 100 characters.
         */
        @NotNull(message = "Payment ID cannot be null")
        @NotBlank(message = "Payment ID cannot be blank or empty")
        @Size(min = 1, max = 100, message = "Payment ID must be between 1 and 100 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]+$",
                message = "Payment ID must contain only alphanumeric characters, hyphens, or underscores"
        )
        String paymentId,

        /**
         * User unique identifier.
         * Must be alphanumeric, between 1 and 100 characters.
         */
        @NotNull(message = "User ID cannot be null")
        @NotBlank(message = "User ID cannot be blank or empty")
        @Size(min = 1, max = 100, message = "User ID must be between 1 and 100 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]+$",
                message = "User ID must contain only alphanumeric characters, hyphens, or underscores"
        )
        String userId,

        /**
         * Payment amount.
         * Must be positive, with maximum 10 digits before decimal point and 2 after.
         * Maximum value: 999,999,999.99 (prevents extremely large values that don't make sense)
         */
        @NotNull(message = "Amount cannot be null")
        @Positive(message = "Amount must be positive (greater than zero)")
        @DecimalMin(value = "0.01", inclusive = true, message = "Amount must be at least 0.01")
        @DecimalMax(
                value = "999999999.99",
                inclusive = true,
                message = "Amount cannot exceed 999,999,999.99 (maximum realistic payment value)"
        )
        @Digits(
                integer = 9,
                fraction = 2,
                message = "Amount must have at most 9 digits before decimal point and exactly 2 after (e.g., 123456789.99)"
        )
        BigDecimal amount,

        /**
         * Currency code in ISO 4217 format.
         * Must be exactly 3 uppercase letters (e.g., BRL, USD, EUR, GBP, JPY).
         */
        @NotNull(message = "Currency cannot be null")
        @NotBlank(message = "Currency cannot be blank or empty")
        @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters (ISO 4217 format)")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "Currency must be 3 uppercase letters (ISO 4217 format: BRL, USD, EUR, etc.)"
        )
        String currency
) {}
