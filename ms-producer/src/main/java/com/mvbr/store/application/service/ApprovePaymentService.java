package com.mvbr.store.application.service;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.in.ApprovePaymentUseCase;
import com.mvbr.store.application.port.out.PaymentRepositoryPort;
import com.mvbr.store.domain.model.PaymentDomain;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import com.mvbr.store.infrastructure.adapter.out.outbox.OutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * USE CASE Service - Approve Payment.
 *
 * Implements the ApprovePaymentUseCase port (inbound port).
 * Depends on outbound ports (PaymentRepositoryPort).
 *
 * This is the APPLICATION LAYER - orchestrates the business logic but doesn't contain it.
 * Business logic lives in PaymentDomain.
 *
 * OUTBOX PATTERN IMPLEMENTED:
 * - Salva Payment + OutboxEvent na mesma transação (atomicidade)
 * - Job assíncrono (OutboxPublisher) publica eventos no Kafka
 * - Garante consistência eventual entre DB e Kafka
 */
@Service
public class ApprovePaymentService implements ApprovePaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final OutboxService outboxService;

    @Value("${spring.kafka.topics.payment-approved}")
    private String paymentApprovedTopic;

    public ApprovePaymentService(
            PaymentRepositoryPort paymentRepository,
            OutboxService outboxService) {
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
    }

    /**
     * Approves a payment and saves event to Outbox.
     *
     * OUTBOX PATTERN:
     * @Transactional ensures Payment + OutboxEvent are saved atomically.
     * OutboxPublisher (scheduled job) will publish to Kafka asynchronously.
     *
     * BUSINESS LOGIC PRESERVED:
     * 1. Convert Command → Domain Model
     * 2. Business validation (constructor validates)
     * 3. Mark payment as approved (state transition)
     * 4. Persist to database
     * 5. Save event to Outbox (same transaction!)
     *
     * CONSISTENCY GUARANTEED:
     * - Both Payment and OutboxEvent committed atomically
     * - If transaction fails, nothing is saved
     * - If transaction succeeds, event WILL be published (eventually)
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
        // 4. Save event to OUTBOX (same transaction!)
        // ============================
        // Criar evento para publicação
        PaymentApprovedEvent event = new PaymentApprovedEvent(
                java.util.UUID.randomUUID().toString(),  // eventId
                savedPayment.getPaymentId(),             // paymentId
                savedPayment.getUserId(),                // userId
                savedPayment.getAmount(),                // amount
                savedPayment.getCurrency(),              // currency
                savedPayment.getStatus().name(),         // status
                Instant.now().toEpochMilli()             // timestamp (Long)
        );

        // Salvar no Outbox (mesma transação do Payment)
        outboxService.saveEvent(
                "PAYMENT",                           // aggregateType
                savedPayment.getPaymentId(),         // aggregateId
                "PAYMENT_APPROVED",                  // eventType
                paymentApprovedTopic,                // topic
                savedPayment.getUserId(),            // partitionKey (userId)
                event                                // payload (será serializado para JSON)
        );

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
