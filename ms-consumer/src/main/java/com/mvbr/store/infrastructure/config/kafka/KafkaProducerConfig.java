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

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // Critical producer settings
    @Value("${spring.kafka.producer.critical.acks}")
    private String criticalAcks;

    @Value("${spring.kafka.producer.critical.enable-idempotence}")
    private Boolean criticalEnableIdempotence;

    @Value("${spring.kafka.producer.critical.retries}")
    private Integer criticalRetries;

    @Value("${spring.kafka.producer.critical.delivery-timeout-ms}")
    private Integer criticalDeliveryTimeoutMs;

    @Value("${spring.kafka.producer.critical.request-timeout-ms}")
    private Integer criticalRequestTimeoutMs;

    @Value("${spring.kafka.producer.critical.max-in-flight-requests-per-connection}")
    private Integer criticalMaxInFlightRequests;

    @Value("${spring.kafka.producer.critical.compression-type}")
    private String criticalCompressionType;

    @Value("${spring.kafka.producer.critical.linger-ms}")
    private Integer criticalLingerMs;

    @Value("${spring.kafka.producer.critical.batch-size}")
    private Integer criticalBatchSize;

    // Default producer settings
    @Value("${spring.kafka.producer.default.acks}")
    private String defaultAcks;

    @Value("${spring.kafka.producer.default.compression-type}")
    private String defaultCompressionType;

    // Fast producer settings
    @Value("${spring.kafka.producer.fast.acks}")
    private String fastAcks;

    @Value("${spring.kafka.producer.fast.linger-ms}")
    private Integer fastLingerMs;

    @Value("${spring.kafka.producer.fast.batch-size}")
    private Integer fastBatchSize;

    // =============================
    // 1 - CRITICAL PRODUCER
    // =============================
    @Bean
    public ProducerFactory<String,Object> criticalProducerFactory() {

        Map<String,Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // === DURABILIDADE MÁXIMA ===
        config.put(ProducerConfig.ACKS_CONFIG, criticalAcks);                                  // líder + réplicas confirmam
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, criticalEnableIdempotence);      // sem duplicatas

        // === RETRY & TIMEOUT ===
        config.put(ProducerConfig.RETRIES_CONFIG, criticalRetries);                            // tenta até conseguir ou dar timeout
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, criticalDeliveryTimeoutMs);      // 2 minutos max
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, criticalRequestTimeoutMs);        // 30s por request

        // === ORDENAÇÃO + THROUGHPUT ===
        // Kafka 0.11+ com idempotence=true permite até 5 e mantém ordem!
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, criticalMaxInFlightRequests);

        // === PERFORMANCE ===
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, criticalCompressionType);           // boa compressão + rápido
        config.put(ProducerConfig.LINGER_MS_CONFIG, criticalLingerMs);                        // agrupa até 10ms
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, criticalBatchSize);                      // 16KB batch

        // === SERIALIZERS ===
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

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
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ACKS_CONFIG, defaultAcks);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, defaultCompressionType);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
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
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ACKS_CONFIG, fastAcks);
        config.put(ProducerConfig.LINGER_MS_CONFIG, fastLingerMs);
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, fastBatchSize);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);

    }

    /*

  ==============================================================================================================

  1. max.in.flight.requests = 5 (antes era 1)

  - Com idempotence=true, o Kafka garante ordem mesmo com 5 requests em paralelo
  - Throughput aumenta até 5x sem perder garantias
  - https://kafka.apache.org/documentation/#producerconfigs_max.in.flight.requests
  .per.connection

  2. retries = Integer.MAX_VALUE (antes era 15)

  - Combinado com delivery.timeout.ms = 120000
  - Tenta reenviar por até 2 minutos antes de falhar
  - Mais resiliente a falhas temporárias de rede

  3. Batching (linger.ms=10, batch.size=16KB)

  - Agrupa mensagens próximas (até 10ms)
  - Reduz overhead de rede
  - Ainda aceita envio imediato se buffer encher

  ✅ Garantias Mantidas

  Sua configuração AINDA TEM:
  - ✅ Zero duplicatas (idempotence=true)
  - ✅ Ordem estrita por partição (max.in.flight ≤ 5)
  - ✅ Durabilidade máxima (acks=all)
  - ✅ Alta disponibilidade (retries infinitos + timeout)

  Agora está production-grade para cenários críticos!

==============================================================================================================

  */

}
