package com.mvbr.store.infrastructure.messaging.consumer;

import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class PaymentApprovedConsumer {

    // =============================
    // 1 - CRITICAL
    // =============================

    @KafkaListener(
            topics = "payment.approved.v1",
            groupId = "payment-service-approved-group",
            containerFactory = "criticalKafkaListenerContainerFactory"
    )
    public void handlePaymentApproved(PaymentApprovedEvent event, Acknowledgment acknowledgment) {

        // Handle deserialization failures gracefully
        if (event == null) {
            System.err.println("\n===== DESERIALIZATION ERROR =====");
            System.err.println("Received null event - message will be retried or sent to DLQ");
            System.err.println("=================================\n");
            // Do NOT acknowledge - let the error handler deal with it
            throw new IllegalArgumentException("Failed to deserialize event - received null");
        }

        try {
            System.out.println("\n===== PAYMENT APPROVED EVENT RECEIVED =====");
            System.out.println("eventId:   " + event.eventId());
            System.out.println("paymentId: " + event.paymentId());
            System.out.println("userId:    " + event.userId());
            System.out.println("amount:    " + event.amount());
            System.out.println("status:    " + event.status());
            System.out.println("timestamp: " + event.timestamp());
            System.out.println("===========================================\n");

            // Manual commit after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                System.out.println("[COMMIT] Offset committed for eventId: " + event.eventId());
            }

        } catch (Exception e) {
            System.err.println("\n===== ERROR PROCESSING EVENT =====");
            System.err.println("Error: " + e.getMessage());
            System.err.println("EventId: " + event.eventId());
            System.err.println("==================================\n");
            // Do NOT acknowledge - message will be retried
            throw new RuntimeException("Failed to process payment approved event", e);
        }
    }

    /*
    ---------------------------------------------------------------------------------------------

     Comportamento correto:

  Caso 1: Evento nulo (erro de deserialização)
  - ❌ NÃO faz commit
  - Lança IllegalArgumentException
  - O DefaultErrorHandler vai retentar 5 vezes (backoff exponencial 1s→10s)
  - Se falhar nas 5 tentativas → Mensagem pode ir para DLQ (se configurado)

  Caso 2: Processamento bem-sucedido
  - ✅ FAZ commit (linha 43)
  - Offset é confirmado
  - Próxima mensagem é consumida

  Caso 3: Erro durante processamento
  - ❌ NÃO faz commit
  - Lança RuntimeException
  - Retry automático pelo DefaultErrorHandler

  Agora mensagens inválidas não são perdidas - elas passam pelo mecanismo de retry e podem ser
  investigadas em uma DLQ posteriormente.

    ---------------------------------------------------------------------------------------------
     */

}
