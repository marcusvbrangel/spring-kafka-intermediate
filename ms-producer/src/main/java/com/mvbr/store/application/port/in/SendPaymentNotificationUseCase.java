package com.mvbr.store.application.port.in;

import com.mvbr.store.application.command.SendPaymentNotificationCommand;

/**
 * INBOUND PORT - Send Payment Notification Use Case.
 *
 * This is a PORT (interface) that defines the contract for sending payment notifications.
 * The implementation will be in the application layer (use case service).
 *
 * In Hexagonal Architecture, this is the PRIMARY PORT (driven by the application).
 */
public interface SendPaymentNotificationUseCase {

    /**
     * Sends a payment notification event to Kafka.
     *
     * @param command the notification command
     */
    void sendNotification(SendPaymentNotificationCommand command);
}
