package com.mvbr.store.consumer;

import com.mvbr.store.event.PaymentNotificationEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentNotificationConsumer {

    @KafkaListener(
            topics = "payment.notification.v1",
            groupId = "payment-service-notification-group",
            containerFactory = "defaultKafkaListenerContainerFactory"
    )
    public void handlePaymentNotification(PaymentNotificationEvent event) {

        System.out.println("===== PAYMENT NOTIFICATION RECEIVED =====");
        System.out.println("eventId:   " + event.eventId());
        System.out.println("paymentId: " + event.paymentId());
        System.out.println("userId:    " + event.userId());
        System.out.println("amount:    " + event.amount());
        System.out.println("message:   " + event.message());
        System.out.println("timestamp: " + event.timestamp());
        System.out.println("==========================================\n");
    }
}