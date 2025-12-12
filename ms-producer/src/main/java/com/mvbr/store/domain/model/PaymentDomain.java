package com.mvbr.store.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Payment Domain Model (Hexagonal Architecture).
 *
 * Pure domain object - NO infrastructure dependencies (no JPA annotations).
 * Contains ALL business logic and validation rules.
 *
 * Uses Builder Pattern following enterprise standards (Google, Netflix, Amazon):
 * - builder() - Returns a new Builder instance
 * - of(...) - Factory method for quick instantiation
 * - from(...) - Converts from DTO/Request objects
 * - with*() - Builder methods for fluent API
 */
public class PaymentDomain {

    private final String paymentId;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;
    private final long createdAt;

    /**
     * Private constructor - use Builder pattern for object creation.
     */
    private PaymentDomain(String paymentId,
                          String userId,
                          BigDecimal amount,
                          String currency,
                          PaymentStatus status,
                          long createdAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    // =======================================
    //      BUILDER PATTERN
    // =======================================

    /**
     * Creates a new Builder instance.
     * @return a new PaymentDomainBuilder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Factory method for quick Payment creation with validation.
     * Creates a new payment with PENDING status.
     *
     * @param paymentId the payment identifier
     * @param userId the user identifier
     * @param amount the payment amount
     * @param currency the currency code
     * @return a new PaymentDomain instance with PENDING status
     */
    public static PaymentDomain of(String paymentId,
                                   String userId,
                                   BigDecimal amount,
                                   String currency) {
        return builder()
                .withPaymentId(paymentId)
                .withUserId(userId)
                .withAmount(amount)
                .withCurrency(currency)
                .build();
    }

    /**
     * Factory method for creating Payment with specific status and timestamp.
     * Useful for testing scenarios where specific states are needed.
     *
     * @param paymentId the payment identifier
     * @param userId the user identifier
     * @param amount the payment amount
     * @param currency the currency code
     * @param status the payment status
     * @param createdAt the creation timestamp
     * @return a new PaymentDomain instance with specified status
     */
    public static PaymentDomain of(String paymentId,
                                   String userId,
                                   BigDecimal amount,
                                   String currency,
                                   PaymentStatus status,
                                   long createdAt) {
        return builder()
                .withPaymentId(paymentId)
                .withUserId(userId)
                .withAmount(amount)
                .withCurrency(currency)
                .withStatus(status)
                .withCreatedAt(createdAt)
                .build();
    }

    /**
     * Creates a PaymentDomain from a PaymentApprovedRequestDto.
     * Common pattern for converting HTTP requests to domain models.
     *
     * @param dto the request DTO from web layer
     * @return a new PaymentDomain instance with PENDING status
     */
    public static PaymentDomain from(com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequestDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("PaymentApprovedRequestDto cannot be null");
        }

        return builder()
                .withPaymentId(dto.paymentId())
                .withUserId(dto.userId())
                .withAmount(dto.amount())
                .withCurrency(dto.currency())
                .build();
    }

    /**
     * Creates a PaymentDomain for RESTORING from database (used by adapters).
     * This method bypasses validation since data is already validated in DB.
     *
     * @param paymentId the payment identifier
     * @param userId the user identifier
     * @param amount the payment amount
     * @param currency the currency code
     * @param status the payment status
     * @param createdAt the creation timestamp
     * @return a restored PaymentDomain instance
     */
    public static PaymentDomain restore(String paymentId,
                                        String userId,
                                        BigDecimal amount,
                                        String currency,
                                        PaymentStatus status,
                                        long createdAt) {
        return new Builder()
                .withPaymentId(paymentId)
                .withUserId(userId)
                .withAmount(amount)
                .withCurrency(currency)
                .withStatus(status)
                .withCreatedAt(createdAt)
                .buildWithoutValidation();
    }

    // =======================================
    //      BUSINESS LOGIC METHODS
    // =======================================

    /**
     * Aprova o pagamento.
     * Valida estado antes de aprovar (não pode aprovar um pagamento cancelado).
     */
    public void markApproved() {
        if (status == PaymentStatus.CANCELED)
            throw new IllegalStateException("Cannot approve a canceled payment");

        this.status = PaymentStatus.APPROVED;
    }

    /**
     * Cancela o pagamento.
     * Valida estado antes de cancelar (não pode cancelar um pagamento aprovado).
     */
    public void cancel() {
        if (status == PaymentStatus.APPROVED)
            throw new IllegalStateException("Cannot cancel an approved payment");

        this.status = PaymentStatus.CANCELED;
    }

    // =======================================
    //      GETTERS
    // =======================================

    public String getPaymentId() { return paymentId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentDomain)) return false;
        PaymentDomain payment = (PaymentDomain) o;
        return paymentId.equals(payment.paymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId);
    }

    @Override
    public String toString() {
        return "PaymentDomain{" +
                "paymentId='" + paymentId + '\'' +
                ", userId='" + userId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }

    // =======================================
    //      BUILDER CLASS
    // =======================================

    /**
     * Builder class following enterprise patterns.
     * Uses 'with' prefix for setters (common in Google, Netflix, Amazon codebases).
     */
    public static class Builder {
        private String paymentId;
        private String userId;
        private BigDecimal amount;
        private String currency;
        private PaymentStatus status;
        private Long createdAt;

        private Builder() {}

        public Builder withPaymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder withAmount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder withCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        /**
         * Sets the payment status.
         * Useful for testing and advanced scenarios.
         */
        public Builder withStatus(PaymentStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the creation timestamp.
         * Useful for testing and advanced scenarios.
         */
        public Builder withCreatedAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Builds the PaymentDomain instance with validation.
         * Defaults: status=PENDING, createdAt=currentTimeMillis
         *
         * @return a new PaymentDomain instance
         * @throws IllegalArgumentException if validation fails
         */
        public PaymentDomain build() {
            validateRequiredFields();

            return new PaymentDomain(
                    paymentId,
                    userId,
                    amount,
                    normalizeCurrency(currency),
                    status != null ? status : PaymentStatus.PENDING,
                    createdAt != null ? createdAt : System.currentTimeMillis()
            );
        }

        /**
         * Builds without validation - used only by restore() method for database reconstruction.
         */
        private PaymentDomain buildWithoutValidation() {
            return new PaymentDomain(
                    paymentId,
                    userId,
                    amount,
                    currency,
                    status != null ? status : PaymentStatus.PENDING,
                    createdAt != null ? createdAt : System.currentTimeMillis()
            );
        }

        private void validateRequiredFields() {
            if (paymentId == null || paymentId.isBlank())
                throw new IllegalArgumentException("paymentId cannot be null or empty");

            if (userId == null || userId.isBlank())
                throw new IllegalArgumentException("userId cannot be null or empty");

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("amount must be greater than zero");

            if (currency == null || currency.isBlank())
                throw new IllegalArgumentException("currency cannot be null or empty");
        }

        private String normalizeCurrency(String currency) {
            return currency != null ? currency.toUpperCase() : null;
        }
    }
}
