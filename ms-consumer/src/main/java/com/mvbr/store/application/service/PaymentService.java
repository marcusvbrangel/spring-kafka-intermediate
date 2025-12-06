package com.mvbr.store.application.service;

import com.mvbr.store.domain.model.Payment;
import com.mvbr.store.infrastructure.persistence.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for payment processing in the CONSUMER microservice.
 *
 * This service is used by Kafka consumers to process and persist payment data.
 * It does NOT produce events (that's the producer's responsibility).
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Processes and persists a payment received from Kafka event.
     *
     * @param payment the payment domain object to be processed (already validated in constructor)
     * @return the persisted payment
     */
    @Transactional
    public Payment processPayment(Payment payment) {
        // Persist payment (validation already done in Payment constructor)
        return paymentRepository.save(payment);
    }

    /**
     * Updates an existing payment status.
     *
     * @param payment the payment with updated data
     * @return the updated payment
     */
    @Transactional
    public Payment updatePayment(Payment payment) {
        return paymentRepository.save(payment);
    }
}
