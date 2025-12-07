package com.mvbr.store.application.port.in;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;

/**
 * INBOUND PORT - Approve Payment Use Case.
 *
 * This is a PORT (interface) that defines the contract for approving payments.
 * The implementation will be in the application layer (use case service).
 *
 * In Hexagonal Architecture, this is the PRIMARY PORT (driven by the application).
 */
public interface ApprovePaymentUseCase {

    /**
     * Approves a payment and publishes event to Kafka.
     *
     * @param command the payment approval command
     * @return the payment response with current status
     */
    PaymentResponse approvePayment(ApprovePaymentCommand command);
}
