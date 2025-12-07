package com.mvbr.store.application.service;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.in.ApprovePaymentUseCase;
import com.mvbr.store.application.port.out.PaymentEventPublisherPort;
import com.mvbr.store.application.port.out.PaymentRepositoryPort;
import com.mvbr.store.domain.model.PaymentDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USE CASE Service - Approve Payment.
 *
 * Implements the ApprovePaymentUseCase port (inbound port).
 * Depends on outbound ports (PaymentRepositoryPort, PaymentEventPublisherPort).
 *
 * This is the APPLICATION LAYER - orchestrates the business logic but doesn't contain it.
 * Business logic lives in PaymentDomain.
 *
 * PRESERVES ALL ORIGINAL BUSINESS LOGIC from PaymentService.approvePayment()
 */
@Service
public class ApprovePaymentService implements ApprovePaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentEventPublisherPort eventPublisher;

    public ApprovePaymentService(
            PaymentRepositoryPort paymentRepository,
            PaymentEventPublisherPort eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Approves a payment and publishes event to Kafka.
     *
     * @Transactional ensures save() and Kafka publishing are atomic.
     * IMPORTANT: Kafka is OUTSIDE the JPA transaction! For full consistency,
     * implement Outbox Pattern in the future.
     *
     * BUSINESS LOGIC PRESERVED:
     * 1. Convert Command → Domain Model
     * 2. Business validation (constructor validates)
     * 3. Mark payment as approved (state transition)
     * 4. Persist to database
     * 5. Publish event to Kafka
     */
    @Transactional
    @Override
    public PaymentResponse approvePayment(ApprovePaymentCommand command) {

        // ============================
        // 1. Create Domain Model (validation in constructor)
        // ============================
        PaymentDomain payment = new PaymentDomain(
                command.paymentId(),
                command.userId(),
                command.amount(),
                command.currency()
        );

        // ============================
        // 2. Business Logic - Mark as approved (state transition)
        // ============================
        payment.markApproved();

        // ============================
        // 3. Persist to database
        // ============================
        PaymentDomain savedPayment = paymentRepository.save(payment);

        // ============================
        // 4. Publish event to Kafka (delegated to adapter)
        // ============================
        eventPublisher.publishPaymentApprovedEvent(savedPayment);

        // ============================
        // 5. Return response
        // ============================
        return new PaymentResponse(
                savedPayment.getPaymentId(),
                savedPayment.getUserId(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getStatus(),
                savedPayment.getCreatedAt()
        );
    }
}
