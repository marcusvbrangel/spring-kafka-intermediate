package com.mvbr.store.infrastructure.config.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    // Critical consumer settings
    @Value("${spring.kafka.consumer.critical.enable-auto-commit}")
    private Boolean criticalEnableAutoCommit;

    @Value("${spring.kafka.consumer.critical.max-poll-records}")
    private Integer criticalMaxPollRecords;

    @Value("${spring.kafka.consumer.critical.concurrency}")
    private Integer criticalConcurrency;

    // Default consumer settings
    @Value("${spring.kafka.consumer.default.enable-auto-commit}")
    private Boolean defaultEnableAutoCommit;

    @Value("${spring.kafka.consumer.default.concurrency}")
    private Integer defaultConcurrency;

    // Fast consumer settings
    @Value("${spring.kafka.consumer.fast.enable-auto-commit}")
    private Boolean fastEnableAutoCommit;

    @Value("${spring.kafka.consumer.fast.max-poll-records}")
    private Integer fastMaxPollRecords;

    @Value("${spring.kafka.consumer.fast.concurrency}")
    private Integer fastConcurrency;

    // Error handling settings
    @Value("${spring.kafka.error.retry.max-attempts}")
    private Integer retryMaxAttempts;

    @Value("${spring.kafka.error.retry.initial-interval-ms}")
    private Long retryInitialIntervalMs;

    @Value("${spring.kafka.error.retry.multiplier}")
    private Double retryMultiplier;

    @Value("${spring.kafka.error.retry.max-interval-ms}")
    private Long retryMaxIntervalMs;

    // =============================
    // COMMON CONFIG FOR ALL
    // =============================
    private Map<String, Object> baseConfig() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // === OFFSET RESET STRATEGY ===
        // "latest" = lê apenas mensagens NOVAS (após o consumer conectar)
        // "earliest" = lê TODAS as mensagens desde o início do tópico
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        // Wrap deserializers with ErrorHandlingDeserializer to handle bad messages gracefully
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Specify the actual deserializers to use
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // JsonDeserializer specific configuration - CRITICAL FIX
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TYPE_MAPPINGS,
            "PaymentApprovedEvent:com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent");

        return props;
    }

    // =============================
    // 1 - CRITICAL (Manual Commit)
    // =============================
    @Bean
    public ConsumerFactory<String, Object> criticalConsumerFactory() {
        Map<String, Object> props = baseConfig();

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, criticalEnableAutoCommit);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, criticalMaxPollRecords);

        // Set default type for JsonDeserializer when no type headers are present
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                  "com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "criticalKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> criticalKafkaListenerContainerFactory(
            @Qualifier("criticalKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate
    ) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(criticalConsumerFactory());
        factory.setConcurrency(criticalConcurrency);

        // === CONFIG CORRETA PARA SPRING 3.x ===
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL
        );

        // === DEAD LETTER QUEUE (DLQ) ===
        // Após falhar N vezes, envia para tópico DLQ: {original-topic}.dlq
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    // Define o tópico DLQ: adiciona sufixo .dlq ao tópico original
                    String dlqTopic = record.topic() + ".dlq";
                    System.err.println("\n===== SENDING TO DLQ =====");
                    System.err.println("Original Topic: " + record.topic());
                    System.err.println("DLQ Topic: " + dlqTopic);
                    System.err.println("Reason: " + ex.getMessage());
                    System.err.println("==========================\n");

                    // Mantém a mesma partição para preservar ordenação por usuário
                    return new TopicPartition(dlqTopic, record.partition());
                }
        );

        // Retry + Backoff + DLQ
        ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(retryMaxAttempts);
        backoff.setInitialInterval(retryInitialIntervalMs);
        backoff.setMultiplier(retryMultiplier);
        backoff.setMaxInterval(retryMaxIntervalMs);

        // DefaultErrorHandler com DeadLetterPublishingRecoverer
        CommonErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backoff);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // =============================
    // 2 - DEFAULT (Auto Commit)
    // =============================
    @Bean
    public ConsumerFactory<String, Object> defaultConsumerFactory() {
        Map<String, Object> props = baseConfig();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, defaultEnableAutoCommit);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "defaultKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> defaultKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(defaultConsumerFactory());
        factory.setConcurrency(defaultConcurrency);

        return factory;
    }

    // =============================
    // 3 - FASTER
    // =============================
    @Bean
    public ConsumerFactory<String, Object> fasterConsumerFactory() {
        Map<String, Object> props = baseConfig();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, fastEnableAutoCommit);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, fastMaxPollRecords);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "fasterKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> fasterKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(fasterConsumerFactory());
        factory.setConcurrency(fastConcurrency);

        return factory;
    }
}