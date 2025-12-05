package com.mvbr.store.model;

import java.math.BigDecimal;
import java.util.Objects;

public class Payment {

    private final String paymentId;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;
    private final long createdAt;

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
    //      NOVOS MÉTODOS PARA SUA REGRA
    // =======================================

    /** Regra extra: pagamento válido significa "construído corretamente" */
    public boolean isValid() {
        return this.paymentId != null &&
                this.userId != null &&
                this.amount != null &&
                this.amount.compareTo(BigDecimal.ZERO) > 0 &&
                this.currency != null &&
                !this.currency.isBlank();
    }

    /** Executa uma aprovação explícita, usada pelo service */
    public void markApproved() {
        if (status == PaymentStatus.CANCELED)
            throw new IllegalStateException("Cannot approve a canceled payment");

        this.status = PaymentStatus.APPROVED;
    }

    // =======================================
    // Métodos existentes continuam funcionando
    // =======================================

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
