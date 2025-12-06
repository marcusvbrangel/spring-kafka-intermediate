package com.mvbr.store.infrastructure.messaging.producer;

import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class PaymentApprovedProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentApprovedProducer.class);

    // =============================
    // 1 - CRITICAL PRODUCER
    // =============================

    private final KafkaTemplate<String, Object> template;

    @Value("${spring.kafka.topics.payment-approved}")
    private String paymentApprovedTopic;

    public PaymentApprovedProducer(@Qualifier("criticalKafkaTemplate") KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    public void producePaymentApproved(PaymentApprovedEvent event) {

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                paymentApprovedTopic,
                event.userId(),       // ordering garantido por userId
                event                 // payload
        );

        record.headers().add(new RecordHeader("event-type", "PAYMENT_APPROVED".getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("service", "payment-service".getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("schema-version", "v1".getBytes(StandardCharsets.UTF_8)));

        // Callback assíncrono para verificar se o broker recebeu a mensagem
        template.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                // ❌ Erro ao enviar - broker não confirmou recebimento
                log.error("CRITICAL: Failed to send payment.approved event for userId={}, paymentId={}: {}",
                          event.userId(), event.paymentId(), ex.getMessage(), ex);
                // Aqui você pode: lançar exceção, salvar em DLQ, alertar monitoramento, etc.
            } else {
                // ✅ Sucesso - broker confirmou recebimento (com acks=all, todas as réplicas confirmaram)
                RecordMetadata metadata = result.getRecordMetadata();
                log.info("Payment.approved event sent successfully - Topic: {}, Partition: {}, Offset: {}, Timestamp: {}, UserId: {}",
                         metadata.topic(),
                         metadata.partition(),
                         metadata.offset(),
                         metadata.timestamp(),
                         event.userId());
            }
        });
    }

}
