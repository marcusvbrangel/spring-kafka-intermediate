package com.mvbr.store.application.mapper;

import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mapper responsável por converter objetos de domínio (Payment)
 * em eventos de infraestrutura (PaymentApprovedEvent).
 *
 * Separação de responsabilidades:
 * - Domain Model: lógica de negócio
 * - Mapper: transformação Domain → Infrastructure Events
 * - Service: orquestração
 */
@Component
public class PaymentEventMapper {

    /**
     * Converte um Payment de domínio em um PaymentApprovedEvent
     * pronto para ser publicado no Kafka.
     *
     * @param payment objeto de domínio com estado aprovado
     * @return evento Kafka com todos os dados necessários
     */
    public PaymentApprovedEvent toPaymentApprovedEvent(Payment payment) {
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

    // Futuramente, você pode adicionar outros métodos aqui:
    // - toPaymentCanceledEvent(Payment payment)
    // - toPaymentRefundedEvent(Payment payment)
    // - etc.
}
