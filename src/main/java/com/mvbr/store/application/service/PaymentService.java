package com.mvbr.store.application.service;

import com.mvbr.store.application.dto.request.PaymentApprovedRequest;
import com.mvbr.store.application.mapper.PaymentEventMapper;
import com.mvbr.store.application.mapper.PaymentRequestMapper;
import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import com.mvbr.store.infrastructure.messaging.producer.PaymentApprovedProducer;
import com.mvbr.store.infrastructure.persistence.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentApprovedProducer paymentApprovedProducer;
    private final PaymentEventMapper paymentEventMapper;
    private final PaymentRequestMapper paymentRequestMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentApprovedProducer paymentApprovedProducer,
            PaymentEventMapper paymentEventMapper,
            PaymentRequestMapper paymentRequestMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentApprovedProducer = paymentApprovedProducer;
        this.paymentEventMapper = paymentEventMapper;
        this.paymentRequestMapper = paymentRequestMapper;
    }

    /**
     * Aprova um pagamento.
     *
     * @Transactional garante que save() e publicação no Kafka sejam atômicos.
     * ATENÇÃO: Kafka está FORA da transação JPA! Para garantia total de consistência,
     * implementar Outbox Pattern no futuro.
     */
    @Transactional
    public void approvePayment(PaymentApprovedRequest request) {

        // ============================
        // 1. Conversão DTO → Domain Model (camada de aplicação)
        // ============================
        Payment payment = paymentRequestMapper.toPayment(request);

        // ============================
        // 2. Lógica de negócio (validação no construtor)
        // ============================
        payment.markApproved();

        // ============================
        // 3. Persistência no banco (TEMPORARIAMENTE COMENTADO - problema com JPA)
        // ============================
        paymentRepository.save(payment);

        // ============================
        // 4. Publicação de evento (delegado ao mapper)
        // ============================
        PaymentApprovedEvent event = paymentEventMapper.toPaymentApprovedEvent(payment);
        paymentApprovedProducer.producePaymentApproved(event);

    }

}
