package com.mvbr.store.infrastructure.adapter.out.persistence.entity;

import com.mvbr.store.domain.model.PaymentStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * JPA Entity for Payment persistence (Infrastructure Layer).
 *
 * This is the INFRASTRUCTURE ADAPTER for database persistence.
 * Contains ONLY JPA annotations - NO business logic.
 *
 * Maps to the "payment" table in PostgreSQL (defined in Flyway migration).
 */
@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    private String paymentId;

    private String userId;
    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private long createdAt;

    // JPA requires a no-arg constructor
    public PaymentEntity() {}

    public PaymentEntity(String paymentId,
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
    //      GETTERS AND SETTERS
    // =======================================

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
