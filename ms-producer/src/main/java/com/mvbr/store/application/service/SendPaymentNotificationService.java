package com.mvbr.store.application.service;

import com.mvbr.store.application.command.SendPaymentNotificationCommand;
import com.mvbr.store.application.port.in.SendPaymentNotificationUseCase;
import com.mvbr.store.application.port.out.PaymentEventPublisherPort;
import org.springframework.stereotype.Service;

/**
 * USE CASE Service - Send Payment Notification.
 *
 * Implements the SendPaymentNotificationUseCase port (inbound port).
 * Depends on outbound port (PaymentEventPublisherPort).
 *
 * This is the APPLICATION LAYER - orchestrates the notification sending.
 *
 * PRESERVES ALL ORIGINAL BUSINESS LOGIC from notification flow.
 */
@Service
public class SendPaymentNotificationService implements SendPaymentNotificationUseCase {

    private final PaymentEventPublisherPort eventPublisher;

    public SendPaymentNotificationService(PaymentEventPublisherPort eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Sends a payment notification event to Kafka.
     *
     * Simple use case - just publishes the event.
     * No persistence required for notifications.
     */
    @Override
    public void sendNotification(SendPaymentNotificationCommand command) {
        eventPublisher.publishPaymentNotificationEvent(
                command.paymentId(),
                command.userId(),
                command.amount(),
                command.message()
        );
    }
}
