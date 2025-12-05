package com.mvbr.store.infrastructure.config.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    // =============================
    // COMMON CONFIG FOR ALL
    // =============================
    private Map<String, Object> baseConfig() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // === OFFSET RESET STRATEGY ===
        // "latest" = lê apenas mensagens NOVAS (após o consumer conectar)
        // "earliest" = lê TODAS as mensagens desde o início do tópico
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        // Wrap deserializers with ErrorHandlingDeserializer to handle bad messages gracefully
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Specify the actual deserializers to use
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // JsonDeserializer specific configuration
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        return props;
    }

    // =============================
    // 1 - CRITICAL (Manual Commit)
    // =============================
    @Bean
    public ConsumerFactory<String, Object> criticalConsumerFactory() {
        Map<String, Object> props = baseConfig();

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "criticalKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> criticalKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(criticalConsumerFactory());
        factory.setConcurrency(1);

        // === CONFIG CORRETA PARA SPRING 3.x ===
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL
        );

        // Retry + Backoff
        ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(1000);
        backoff.setMultiplier(2);
        backoff.setMaxInterval(10000);

        CommonErrorHandler errorHandler = new DefaultErrorHandler(backoff);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // =============================
    // 2 - DEFAULT (Auto Commit)
    // =============================
    @Bean
    public ConsumerFactory<String, Object> defaultConsumerFactory() {
        Map<String, Object> props = baseConfig();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "defaultKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> defaultKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(defaultConsumerFactory());
        factory.setConcurrency(3);

        return factory;
    }

    // =============================
    // 3 - FASTER
    // =============================
    @Bean
    public ConsumerFactory<String, Object> fasterConsumerFactory() {
        Map<String, Object> props = baseConfig();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "fasterKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> fasterKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(fasterConsumerFactory());
        factory.setConcurrency(8);

        return factory;
    }
}