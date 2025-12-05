package com.mvbr.store.service;

import com.mvbr.store.event.PaymentApprovedEvent;
import com.mvbr.store.model.Payment;
import com.mvbr.store.producer.PaymentApprovedProducer;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    // repository ????????

    private final PaymentApprovedProducer paymentApprovedProducer;

    public PaymentService(PaymentApprovedProducer paymentApprovedProducer) {
        this.paymentApprovedProducer = paymentApprovedProducer;
    }

    public void approvePayment(Payment payment) {

        // ============================
        // Regras de negócio aqui
        // ============================
        if (!payment.isValid()) {
            throw new IllegalArgumentException("Invalid payment");
        }

        payment.markApproved();

        // =======================================
        // Agora constrói o evento COMPLETO
        // =======================================
        PaymentApprovedEvent event = new PaymentApprovedEvent(
                UUID.randomUUID().toString(),      // eventId
                payment.getPaymentId(),            // paymentId
                payment.getUserId(),               // userId
                payment.getAmount(),               // amount (BigDecimal)
                payment.getCurrency(),             // currency (String)
                payment.getStatus().name(),        // status (ex: "APPROVED")
                System.currentTimeMillis()         // timestamp (Long)
        );

        // =======================================
        // dispara o evento
        // =======================================
        paymentApprovedProducer.producePaymentApproved(event);

    }

}
