package com.mvbr.store.infrastructure.adapter.in.web.mapper;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.command.SendPaymentNotificationCommand;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequestDto;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentNotificationRequestDto;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponseDto;
import org.springframework.stereotype.Component;

/**
 * Mapper between Web DTOs (infrastructure) and Application Commands/Responses.
 *
 * This is the ANTI-CORRUPTION LAYER - prevents HTTP/REST concerns from leaking into the application.
 */
@Component
public class PaymentWebMapper {

    /**
     * Converts PaymentApprovedRequestDto (web) to ApprovePaymentCommand (application).
     */
    public ApprovePaymentCommand toApprovePaymentCommand(PaymentApprovedRequestDto dto) {
        return new ApprovePaymentCommand(
                dto.paymentId(),
                dto.userId(),
                dto.amount(),
                dto.currency()
        );
    }

    /**
     * Converts PaymentNotificationRequestDto (web) to SendPaymentNotificationCommand (application).
     */
    public SendPaymentNotificationCommand toSendNotificationCommand(PaymentNotificationRequestDto dto) {
        return new SendPaymentNotificationCommand(
                dto.paymentId(),
                dto.userId(),
                dto.amount(),
                dto.message()
        );
    }

    /**
     * Converts PaymentResponse (application) to PaymentResponseDto (web).
     */
    public PaymentResponseDto toPaymentResponseDto(PaymentResponse response) {
        return new PaymentResponseDto(
                response.paymentId(),
                response.userId(),
                response.amount(),
                response.currency(),
                response.status().name(),
                response.createdAt()
        );
    }
}
