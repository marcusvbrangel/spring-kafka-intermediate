package com.mvbr.store.infrastructure.adapter.in.web;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.in.ApprovePaymentUseCase;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequestDto;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponseDto;
import com.mvbr.store.infrastructure.adapter.in.web.mapper.PaymentWebMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
@Slf4j
@RestController
@RequestMapping("/api/payments")
public class PaymentWebController {

    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final PaymentWebMapper mapper;

    public PaymentWebController(
            ApprovePaymentUseCase approvePaymentUseCase,
            PaymentWebMapper mapper) {
        this.approvePaymentUseCase = approvePaymentUseCase;
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
     * 2. Validates request (@Valid triggers Jakarta Bean Validation)
     * 3. Converts DTO → Command
     * 4. Delegates to Use Case
     * 5. Converts Response → DTO
     * 6. Returns HTTP response
     *
     * VALIDATION:
     * - If validation fails, Spring returns HTTP 400 (Bad Request) with error details
     * - Validation happens BEFORE entering the method body
     */
    @PostMapping("/approved")
    public ResponseEntity<PaymentResponseDto> publishPaymentApproved(@Valid @RequestBody PaymentApprovedRequestDto request) {

        simulateTimeError(request);

        // DTO → Command (infrastructure → application)
        ApprovePaymentCommand command = mapper.toApprovePaymentCommand(request);

        // Delegate to Use Case (application layer)
        PaymentResponse response = approvePaymentUseCase.approvePayment(command);

        // Response → DTO (application → infrastructure)
        return ResponseEntity.ok(mapper.toPaymentResponseDto(response));
    }

    /**
     * Simula cenários de observabilidade baseado no amount do pagamento.
     *
     * - amount = 600: sleep 2s + log WARN
     * - amount = 700: sleep 8s + log WARN
     * - amount = 800: sleep 10s + log ERROR + throw RuntimeException
     */
    private void simulateTimeError(PaymentApprovedRequestDto request) {
        var amount = request.amount();

        if (amount == null) {
            return;
        }

        try {
            if (amount.intValue() == 600) {
                log.warn("⚠️ OBSERVABILITY TEST - Amount 600: Simulating SLOW operation (2 seconds). PaymentId: {}, UserId: {}",
                    request.paymentId(), request.userId());
                Thread.sleep(2000);
                log.warn("⚠️ OBSERVABILITY TEST - Amount 600: Slow operation completed. PaymentId: {}", request.paymentId());

            } else if (amount.intValue() == 700) {
                log.warn("⚠️ OBSERVABILITY TEST - Amount 700: Simulating VERY SLOW operation (8 seconds). PaymentId: {}, UserId: {}",
                    request.paymentId(), request.userId());
                Thread.sleep(8000);
                log.warn("⚠️ OBSERVABILITY TEST - Amount 700: Very slow operation completed. PaymentId: {}", request.paymentId());

            } else if (amount.intValue() == 800) {
                log.error("🔥 OBSERVABILITY TEST - Amount 800: Simulating CRITICAL ERROR (10s + exception). PaymentId: {}, UserId: {}",
                    request.paymentId(), request.userId());
                Thread.sleep(10000);
                log.error("🔥 OBSERVABILITY TEST - Amount 800: About to throw RuntimeException! PaymentId: {}", request.paymentId());
                throw new RuntimeException("OBSERVABILITY TEST: Critical error for amount 800 - Payment processing failed!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted during observability simulation", e);
        }
    }

}
