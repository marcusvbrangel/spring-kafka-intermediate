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

    public Payment() {}

    public Payment(String paymentId,
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
