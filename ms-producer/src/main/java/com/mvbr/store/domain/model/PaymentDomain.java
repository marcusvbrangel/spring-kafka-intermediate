package com.mvbr.store.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Payment Domain Model (Hexagonal Architecture).
 *
 * Pure domain object - NO infrastructure dependencies (no JPA annotations).
 * Contains ALL business logic and validation rules.
 *
 * This is the SAME logic from the original Payment.java,
 * but without @Entity and @Id annotations.
 */
public class PaymentDomain {

    private final String paymentId;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;
    private final long createdAt;

    // Constructor for creating NEW payments
    public PaymentDomain(String paymentId,
                         String userId,
                         BigDecimal amount,
                         String currency) {

        if (paymentId == null || paymentId.isBlank())
            throw new IllegalArgumentException("paymentId cannot be null or empty");

        if (userId == null || userId.isBlank())
            throw new IllegalArgumentException("userId cannot be null or empty");

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be greater than zero");

        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("currency cannot be null or empty");

        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency.toUpperCase();
        this.status = PaymentStatus.PENDING;
        this.createdAt = System.currentTimeMillis();
    }

    // Constructor for RESTORING payments from database (used by adapters)
    public PaymentDomain(String paymentId,
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
}
