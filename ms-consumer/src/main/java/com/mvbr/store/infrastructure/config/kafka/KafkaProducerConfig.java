package com.mvbr.store.infrastructure.config.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration for MS-CONSUMER.
 *
 * NOTE: Even though this is a consumer microservice, we need a minimal producer
 * configuration for DLQ reprocessing (DLQReprocessor republishes messages).
 *
 * Only CRITICAL profile is configured (used by DLQReprocessor).
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * CRITICAL Producer Factory - For DLQ reprocessing only.
     *
     * Configuration mirrors the producer microservice to ensure same delivery guarantees:
     * - acks=all: Leader + all replicas must confirm (maximum durability)
     * - enable.idempotence=true: No duplicates even with retries
     * - retries=Integer.MAX_VALUE: Retry indefinitely until success/timeout
     * - max.in.flight.requests=5: Maintains ordering with idempotence
     * - compression.type=snappy: Fast compression with good ratio
     */
    @Bean
    public ProducerFactory<String, Object> criticalProducerFactory() {
        Map<String, Object> props = new HashMap<>();

        // Kafka broker
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serialization
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // CRITICAL: Maximum reliability settings
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Compression
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Batch settings
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * CRITICAL KafkaTemplate - Used by DLQReprocessor to republish messages.
     *
     * This is the ONLY KafkaTemplate in the consumer microservice.
     * It's specifically used for DLQ reprocessing, not for general message production.
     */
    @Bean(name = "criticalKafkaTemplate")
    public KafkaTemplate<String, Object> criticalKafkaTemplate() {
        return new KafkaTemplate<>(criticalProducerFactory());
    }
}
