package com.mvbr.store.application.mapper;

import com.mvbr.store.application.dto.request.PaymentApprovedRequest;
import com.mvbr.store.domain.model.Payment;
import org.springframework.stereotype.Component;

/**
 * Mapper responsável por converter DTOs de requisição em objetos de domínio.
 *
 * Separação de responsabilidades:
 * - Application Layer: conhece DTOs e Domain Models
 * - Domain Layer: NÃO conhece DTOs (independente de protocolos HTTP/gRPC/etc)
 *
 * Esta classe resolve a violação de arquitetura onde Payment.fromRequest()
 * criava dependência do Domínio → Aplicação.
 */
@Component
public class PaymentRequestMapper {

    /**
     * Converte um DTO de requisição em um Payment de domínio.
     *
     * @param request DTO recebido da camada de apresentação (REST, gRPC, etc)
     * @return objeto Payment do domínio, pronto para aplicar regras de negócio
     */
    public Payment toPayment(PaymentApprovedRequest request) {
        return new Payment(
                request.paymentId(),
                request.userId(),
                request.amount(),
                request.currency()
        );
    }

    // Futuramente, você pode adicionar outros métodos aqui:
    // - toPayment(PaymentNotificationRequest request)
    // - toPayment(PaymentGrpcRequest request)
    // - etc.
}
