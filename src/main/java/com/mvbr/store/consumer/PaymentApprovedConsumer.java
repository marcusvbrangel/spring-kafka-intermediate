package com.mvbr.store.consumer;

import com.mvbr.store.event.PaymentApprovedEvent;
import org.springframework.kafka.annotation.KafkaListener;
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
    public void handlePaymentApproved(PaymentApprovedEvent event) {

        // Handle deserialization failures gracefully
        if (event == null) {
            System.err.println("\n===== DESERIALIZATION ERROR =====");
            System.err.println("Received null event - skipping bad message");
            System.err.println("=================================\n");
            return;
        }

        System.out.println("\n===== PAYMENT APPROVED EVENT RECEIVED =====");
        System.out.println("eventId:   " + event.eventId());
        System.out.println("paymentId: " + event.paymentId());
        System.out.println("userId:    " + event.userId());
        System.out.println("amount:    " + event.amount());
        System.out.println("status:    " + event.status());
        System.out.println("timestamp: " + event.timestamp());
        System.out.println("===========================================\n");
    }

}
