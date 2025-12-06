package com.mvbr.store.application.controller;

import com.mvbr.store.application.dto.request.PaymentApprovedRequest;
import com.mvbr.store.application.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        // Delega para o Service, que faz a conversão DTO → Model internamente
        paymentService.approvePayment(req);

        return "PaymentApprovedEvent enviado com sucesso!";

    }

}
