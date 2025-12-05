package com.mvbr.store.producer;

import com.mvbr.store.event.PaymentNotificationEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentNotificationProducer {

    // =============================
    // 2 - DEFAULT PRODUCER
    // =============================

    private final KafkaTemplate<String, Object> template;

    public PaymentNotificationProducer(@Qualifier("defaultKafkaTemplate") KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    public void sendPaymentNotification(PaymentNotificationEvent event) {

        // A chave será userId → envia todas as notificações de um usuário
        // para a mesma partição → mantém ordenação
        template.send("payment.notification.v1", event.userId(), event);
    }
}