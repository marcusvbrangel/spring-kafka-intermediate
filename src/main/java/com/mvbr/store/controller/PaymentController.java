package com.mvbr.store.controller;

import com.mvbr.store.dto.PaymentApprovedRequest;
import com.mvbr.store.dto.PaymentNotificationRequest;
import com.mvbr.store.event.PaymentNotificationEvent;
import com.mvbr.store.model.Payment;
import com.mvbr.store.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ========================================================
    // 1. Pagamento aprovado — CRÍTICO
    // ========================================================
    @PostMapping("/approved")
    public String publishPaymentApproved(@RequestBody PaymentApprovedRequest req) {

        var payment = new Payment(req.paymentId(), req.userId(), req.amount(), req.currency());

        paymentService.approvePayment(payment);

        return "PaymentApprovedEvent enviado com sucesso!";

    }

    // ========================================================
    // 2. Notificação de pagamento — DEFAULT PRODUCER
    // ========================================================
    @PostMapping("/notify")
    public String sendPaymentNotification(@RequestBody PaymentNotificationRequest req) {

        PaymentNotificationEvent event = new PaymentNotificationEvent(
                UUID.randomUUID().toString(),
                req.paymentId(),
                req.userId(),
                req.amount(),
                req.message(),
                System.currentTimeMillis()
        );

//        paymentNotificationProducer.sendPaymentNotification(event);

        return "PaymentNotificationEvent enviado com sucesso!";
    }

}
