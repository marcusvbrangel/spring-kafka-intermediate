package com.mvbr.store.application.port.out;

import com.mvbr.store.domain.model.PaymentDomain;

/**
 * OUTBOUND PORT - Payment Event Publishing.
 *
 * This is a PORT (interface) that defines the contract for publishing payment events to Kafka.
 * The implementation will be in the infrastructure layer (Kafka adapter).
 *
 * In Hexagonal Architecture, this is the SECONDARY PORT (driven by the domain).
 * The domain DOES NOT depend on Kafka - it depends on THIS interface.
 */
public interface PaymentEventPublisherPort {

    /**
     * Publishes a payment approved event to Kafka.
     *
     * @param payment the payment domain object that was approved
     */
    void publishPaymentApprovedEvent(PaymentDomain payment);

    /**
     * Publishes a payment notification event to Kafka.
     *
     * @param paymentId the payment identifier
     * @param userId the user identifier
     * @param amount the payment amount
     * @param message the notification message
     */
    void publishPaymentNotificationEvent(String paymentId, String userId, double amount, String message);
}
