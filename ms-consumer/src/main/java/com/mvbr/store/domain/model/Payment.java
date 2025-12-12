package com.mvbr.store.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
public class Payment {

    @Id
    private String paymentId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private long createdAt;

    /**
     * Default constructor for JPA.
     * For creating new instances, use Payment.builder() or Payment.of()
     */
    public Payment() {}

    /**
     * Private constructor - use Builder pattern for object creation.
     */
    private Payment(String paymentId,
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
     * @return a new PaymentBuilder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Factory method for quick Payment creation with validation.
     *
     * @param paymentId the payment identifier
     * @param userId the user identifier
     * @param amount the payment amount
     * @param currency the currency code
     * @return a new Payment instance with PENDING status
     */
    public static Payment of(String paymentId,
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
     * Creates a Payment from a PaymentApprovedEvent (common pattern for event-driven systems).
     * Maps event fields to domain model, useful for Kafka consumers.
     *
     * @param event the PaymentApprovedEvent from Kafka topic
     * @return a new Payment instance based on the event data
     */
    public static Payment from(com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("PaymentApprovedEvent cannot be null");
        }

        return builder()
                .withPaymentId(event.paymentId())
                .withUserId(event.userId())
                .withAmount(event.amount())
                .withCurrency(event.currency())
                .build();
    }

    /**
     * Builder class following enterprise patterns.
     * Uses 'with' prefix for setters (common in Google, Netflix codebases).
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
         * Internal method for setting status (used by from* methods).
         */
        private Builder withStatus(PaymentStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Internal method for setting createdAt (used by from* methods).
         */
        private Builder withCreatedAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Builds the Payment instance with validation.
         * Defaults: status=PENDING, createdAt=currentTimeMillis
         *
         * @return a new Payment instance
         * @throws IllegalArgumentException if validation fails
         */
        public Payment build() {
            validateRequiredFields();

            return new Payment(
                    paymentId,
                    userId,
                    amount,
                    normalizeCurrency(currency),
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

    public String getPaymentId() { return paymentId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment)) return false;
        Payment payment = (Payment) o;
        return paymentId.equals(payment.paymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId);
    }

}
