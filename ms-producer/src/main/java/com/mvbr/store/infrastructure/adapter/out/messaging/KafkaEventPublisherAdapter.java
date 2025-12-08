package com.mvbr.store.infrastructure.adapter.out.messaging;

import com.mvbr.store.application.port.out.PaymentEventPublisherPort;
import com.mvbr.store.domain.model.PaymentDomain;
import com.mvbr.store.infrastructure.adapter.out.messaging.mapper.PaymentEventMapper;
import com.mvbr.store.infrastructure.adapter.out.messaging.producer.PaymentApprovedProducer;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.springframework.stereotype.Component;

/**
 * OUTBOUND ADAPTER - Kafka Event Publishing Implementation.
 *
 * Implements PaymentEventPublisherPort (outbound port from application layer).
 * Uses Kafka Producers for actual message publishing.
 *
 * This is the ADAPTER that translates between:
 * - Domain layer (PaymentDomain)
 * - Infrastructure layer (Kafka Events + Producers)
 *
 * DEPENDENCY DIRECTION: Application → Port ← Adapter (Dependency Inversion!)
 *
 * PRESERVES ALL ORIGINAL KAFKA PUBLISHING LOGIC:
 * - PaymentApprovedProducer with CRITICAL profile (acks=all)
 * - Headers, ordering, callbacks - all preserved
 */
@Component
public class KafkaEventPublisherAdapter implements PaymentEventPublisherPort {

    private final PaymentApprovedProducer paymentApprovedProducer;
    private final PaymentEventMapper eventMapper;

    public KafkaEventPublisherAdapter(
            PaymentApprovedProducer paymentApprovedProducer,
            PaymentEventMapper eventMapper) {
        this.paymentApprovedProducer = paymentApprovedProducer;
        this.eventMapper = eventMapper;
    }

    /**
     * Publishes payment approved event to Kafka.
     *
     * ORIGINAL FLOW PRESERVED:
     * 1. Domain → Event (via mapper)
     * 2. Event → Kafka (via producer with CRITICAL profile)
     */
    @Override
    public void publishPaymentApprovedEvent(PaymentDomain payment) {
        PaymentApprovedEvent event = eventMapper.toPaymentApprovedEvent(payment);
        paymentApprovedProducer.producePaymentApproved(event);
    }
}
