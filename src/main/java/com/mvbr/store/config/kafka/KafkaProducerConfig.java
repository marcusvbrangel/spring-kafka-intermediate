package com.mvbr.store.config.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    // =============================
    // 1 - CRITICAL PRODUCER
    // =============================
    @Bean
    public ProducerFactory<String,Object> criticalProducerFactory() {

        Map<String,Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRIES_CONFIG, 15);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaProducerFactory<>(config);

    }

    @Bean(name = "criticalKafkaTemplate")
    public KafkaTemplate<String, Object> criticalKafkaTemplate() {
        return new KafkaTemplate<>(criticalProducerFactory());
    }

    // =============================
    // 2 - DEFAULT PRODUCER
    // =============================
    @Bean
    public ProducerFactory<String, Object> defaultProducerFactory() {

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.ACKS_CONFIG, 1);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaProducerFactory<>(config);

    }

    @Bean(name = "defaultKafkaTemplate")
    public KafkaTemplate<String, Object> defaultKafkaTemplate() {
        return new KafkaTemplate<>(defaultProducerFactory());
    }

    // =============================
    // 3 - FIRE-AND-FORGET PRODUCER
    // =============================
    @Bean
    public ProducerFactory<String, Object> fastProducerFactory() {

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.ACKS_CONFIG, 0);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 50);
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        return new DefaultKafkaProducerFactory<>(config);

    }

}
