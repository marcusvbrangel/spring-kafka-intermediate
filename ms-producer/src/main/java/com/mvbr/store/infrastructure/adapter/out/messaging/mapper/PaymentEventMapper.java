package com.mvbr.store.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.store.domain.model.PaymentDomain;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import com.mvbr.store.infrastructure.messaging.event.PaymentNotificationEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mapper for converting Domain objects to Kafka Events (Infrastructure Layer).
 *
 * This is the ANTI-CORRUPTION LAYER - prevents Kafka concerns from leaking into the domain.
 *
 * PRESERVES ALL ORIGINAL MAPPING LOGIC from PaymentEventMapper.
 */
@Component
public class PaymentEventMapper {

    /**
     * Converts PaymentDomain to PaymentApprovedEvent.
     *
     * ORIGINAL LOGIC PRESERVED:
     * - Generates unique eventId (UUID)
     * - Maps all payment fields to event
     * - Adds timestamp
     *
     * @param payment the domain object with approved status
     * @return Kafka event ready to be published
     */
    public PaymentApprovedEvent toPaymentApprovedEvent(PaymentDomain payment) {
        return new PaymentApprovedEvent(
                UUID.randomUUID().toString(),      // eventId único
                payment.getPaymentId(),            // paymentId
                payment.getUserId(),               // userId
                payment.getAmount(),               // amount (BigDecimal)
                payment.getCurrency(),             // currency (String)
                payment.getStatus().name(),        // status (ex: "APPROVED")
                System.currentTimeMillis()         // timestamp (Long)
        );
    }

    /**
     * Creates PaymentNotificationEvent from raw data.
     *
     * @param paymentId the payment identifier
     * @param userId the user identifier
     * @param amount the payment amount
     * @param message the notification message
     * @return Kafka event ready to be published
     */
    public PaymentNotificationEvent toPaymentNotificationEvent(
            String paymentId,
            String userId,
            double amount,
            String message) {

        return new PaymentNotificationEvent(
                UUID.randomUUID().toString(),   // eventId único
                paymentId,
                userId,
                amount,
                message,
                System.currentTimeMillis()      // timestamp
        );
    }
}
