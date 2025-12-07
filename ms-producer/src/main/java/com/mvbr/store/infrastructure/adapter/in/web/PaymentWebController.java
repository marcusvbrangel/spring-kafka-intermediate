package com.mvbr.store.infrastructure.adapter.in.web;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.command.SendPaymentNotificationCommand;
import com.mvbr.store.application.port.in.ApprovePaymentUseCase;
import com.mvbr.store.application.port.in.SendPaymentNotificationUseCase;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequestDto;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentNotificationRequestDto;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponseDto;
import com.mvbr.store.infrastructure.adapter.in.web.mapper.PaymentWebMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * INBOUND ADAPTER - Payment Web Controller (REST API).
 *
 * This is the INFRASTRUCTURE layer - handles HTTP requests and responses.
 * Depends on PORTS (use cases), not on concrete implementations.
 *
 * DEPENDENCY DIRECTION: Adapter → Port ← Implementation (Dependency Inversion!)
 *
 * PRESERVES ALL ORIGINAL REST API CONTRACT:
 * - Same endpoints: POST /api/payments/approved
 * - Same request/response structure
 * - Same behavior
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentWebController {

    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final SendPaymentNotificationUseCase sendNotificationUseCase;
    private final PaymentWebMapper mapper;

    public PaymentWebController(
            ApprovePaymentUseCase approvePaymentUseCase,
            SendPaymentNotificationUseCase sendNotificationUseCase,
            PaymentWebMapper mapper) {
        this.approvePaymentUseCase = approvePaymentUseCase;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.mapper = mapper;
    }

    // ========================================================
    // 1. Pagamento aprovado — CRÍTICO
    // ========================================================
    /**
     * POST /api/payments/approved
     *
     * ORIGINAL FLOW PRESERVED:
     * 1. Receives HTTP request (DTO)
     * 2. Converts DTO → Command
     * 3. Delegates to Use Case
     * 4. Converts Response → DTO
     * 5. Returns HTTP response
     */
    @PostMapping("/approved")
    public PaymentResponseDto publishPaymentApproved(@RequestBody PaymentApprovedRequestDto request) {

        // DTO → Command (infrastructure → application)
        ApprovePaymentCommand command = mapper.toApprovePaymentCommand(request);

        // Delegate to Use Case (application layer)
        PaymentResponse response = approvePaymentUseCase.approvePayment(command);

        // Response → DTO (application → infrastructure)
        return mapper.toPaymentResponseDto(response);
    }

    // ========================================================
    // 2. Notificação de pagamento — DEFAULT
    // ========================================================
    /**
     * POST /api/payments/notification
     *
     * Sends a payment notification event to Kafka.
     */
    @PostMapping("/notification")
    public String publishPaymentNotification(@RequestBody PaymentNotificationRequestDto request) {

        // DTO → Command (infrastructure → application)
        SendPaymentNotificationCommand command = mapper.toSendNotificationCommand(request);

        // Delegate to Use Case (application layer)
        sendNotificationUseCase.sendNotification(command);

        return "PaymentNotificationEvent enviado com sucesso!";
    }
}
