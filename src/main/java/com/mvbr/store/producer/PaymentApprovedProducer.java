package com.mvbr.store.producer;

import com.mvbr.store.event.PaymentApprovedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class PaymentApprovedProducer {

    // =============================
    // 1 - CRITICAL PRODUCER
    // =============================

    private final KafkaTemplate<String, Object> template;

    public PaymentApprovedProducer(@Qualifier("criticalKafkaTemplate") KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    public void producePaymentApproved(PaymentApprovedEvent event) {

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                "payment.approved.v1",
                event.userId(),       // ordering garantido por userId
                event                 // payload
        );

        record.headers().add(new RecordHeader("event-type", "PAYMENT_APPROVED".getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("service", "payment-service".getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("schema-version", "v1".getBytes(StandardCharsets.UTF_8)));

        template.send(record);
    }

}
