package com.mvbr.store.infrastructure.adapter.out.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OutboxPublisher - Job assíncrono que publica eventos do Outbox no Kafka.
 *
 * OUTBOX PATTERN - SEGUNDA FASE:
 * 1. Busca eventos PENDING da tabela outbox
 * 2. Publica no Kafka
 * 3. Marca como PUBLISHED (ou FAILED após max retries)
 *
 * CONFIGURAÇÃO:
 * - Executa a cada X ms (configurável)
 * - Processa Y eventos por vez (batch)
 * - Retry automático com backoff
 *
 * IMPORTANTE:
 * - Idempotência (pode processar mesmo evento múltiplas vezes)
 * - At-least-once delivery (evento pode ser publicado mais de 1x)
 * - Kafka deve ter idempotência habilitada (acks=all, enable.idempotence=true)
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final OutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${outbox.publisher.batch-size:100}")
    private int batchSize;

    @Value("${outbox.publisher.max-retries:3}")
    private int maxRetries;

    public OutboxPublisher(OutboxEventRepository outboxRepository,
                           OutboxService outboxService,
                           @Qualifier("criticalKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Job agendado que processa eventos pendentes.
     *
     * FREQUÊNCIA:
     * - Default: a cada 5 segundos
     * - Configurável via application.yaml
     *
     * FLUXO:
     * 1. Busca até N eventos PENDING
     * 2. Para cada evento:
     *    - Publica no Kafka
     *    - Se sucesso → marca PUBLISHED
     *    - Se erro → incrementa retry, se > maxRetries → marca FAILED
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:5000}")
    public void publishPendingEvents() {
        try {
            // Buscar eventos pendentes (batch)
            List<OutboxEvent> pendingEvents = outboxRepository
                    .findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, batchSize);

            if (pendingEvents.isEmpty()) {
                log.debug("No pending outbox events to publish");
                return;
            }

            log.info("Found {} pending outbox events to publish", pendingEvents.size());

            // Processar cada evento
            for (OutboxEvent event : pendingEvents) {
                try {
                    publishEvent(event);
                } catch (Exception e) {
                    log.error("Failed to publish outbox event: id={}, error={}",
                            event.getId(), e.getMessage(), e);
                    handlePublishError(event, e);
                }
            }

        } catch (Exception e) {
            log.error("Error in outbox publisher job", e);
        }
    }

    /**
     * Publica um evento específico no Kafka.
     *
     * @param outboxEvent evento a publicar
     */
    @Transactional
    protected void publishEvent(OutboxEvent outboxEvent) {
        try {
            // Desserializar payload JSON para o tipo correto baseado no eventType
            Object payload = deserializePayload(outboxEvent);

            // Extrair eventId do payload (evento de domínio)
            String domainEventId = extractEventId(payload, outboxEvent.getEventType());

            // =============================
            // LOG: INICIANDO PUBLICAÇÃO
            // =============================
            log.info("\n" +
                    "=================================================================\n" +
                    "                  📤 OUTBOX → KAFKA PUBLISHER                    \n" +
                    "=================================================================\n" +
                    "  Outbox ID (Tabela):  {}\n" +
                    "  Event ID (Domínio):  {}\n" +
                    "  Event Type:          {}\n" +
                    "  Aggregate ID:        {}\n" +
                    "  Topic:               {}\n" +
                    "  Partition Key:       {}\n" +
                    "=================================================================",
                    outboxEvent.getId(),
                    domainEventId,
                    outboxEvent.getEventType(),
                    outboxEvent.getAggregateId(),
                    outboxEvent.getTopic(),
                    outboxEvent.getPartitionKey()
            );

            // Criar ProducerRecord com headers
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    outboxEvent.getTopic(),
                    outboxEvent.getPartitionKey(),
                    payload
            );

            // Adicionar headers
            record.headers().add(new RecordHeader(
                    "event-type",
                    outboxEvent.getEventType().getBytes(StandardCharsets.UTF_8)
            ));
            record.headers().add(new RecordHeader(
                    "event-id",
                    domainEventId.getBytes(StandardCharsets.UTF_8)
            ));
            record.headers().add(new RecordHeader(
                    "aggregate-id",
                    outboxEvent.getAggregateId().getBytes(StandardCharsets.UTF_8)
            ));
            record.headers().add(new RecordHeader(
                    "source",
                    "outbox-publisher".getBytes(StandardCharsets.UTF_8)
            ));

            // Publicar no Kafka (síncrono para garantir ordem)
            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    // =============================
                    // LOG: ERRO AO PUBLICAR
                    // =============================
                    log.error("\n" +
                            "=================================================================\n" +
                            "                    ❌ KAFKA PUBLISH FAILED                      \n" +
                            "=================================================================\n" +
                            "  Outbox ID:     {}\n" +
                            "  Event ID:      {}\n" +
                            "  Error:         {}\n" +
                            "=================================================================",
                            outboxEvent.getId(),
                            domainEventId,
                            ex.getMessage()
                    );
                    handlePublishError(outboxEvent, ex);
                } else {
                    // =============================
                    // LOG: SUCESSO NA PUBLICAÇÃO
                    // =============================
                    log.info("\n" +
                            "=================================================================\n" +
                            "                  ✅ KAFKA PUBLISH SUCCESSFUL                    \n" +
                            "=================================================================\n" +
                            "  Outbox ID:       {}\n" +
                            "  Event ID:        {}\n" +
                            "  Topic:           {}\n" +
                            "  Partition:       {}\n" +
                            "  Offset:          {}\n" +
                            "  Timestamp:       {}\n" +
                            "=================================================================",
                            outboxEvent.getId(),
                            domainEventId,
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            result.getRecordMetadata().timestamp()
                    );

                    // Marcar como publicado
                    outboxService.markAsPublished(outboxEvent.getId());
                }
            }).get(); // Bloqueia para garantir processamento sequencial

        } catch (Exception e) {
            // =============================
            // LOG: ERRO CRÍTICO
            // =============================
            log.error("\n" +
                    "=================================================================\n" +
                    "                    🔥 CRITICAL PUBLISH ERROR                    \n" +
                    "=================================================================\n" +
                    "  Outbox ID:     {}\n" +
                    "  Event Type:    {}\n" +
                    "  Error:         {}\n" +
                    "  Stack Trace:   See below\n" +
                    "=================================================================",
                    outboxEvent.getId(),
                    outboxEvent.getEventType(),
                    e.getMessage(),
                    e
            );
            throw new RuntimeException("Failed to publish outbox event", e);
        }
    }

    /**
     * Trata erro de publicação.
     *
     * ESTRATÉGIA:
     * - Se retry_count < maxRetries → incrementa retry e tenta novamente
     * - Se retry_count >= maxRetries → marca como FAILED
     *
     * @param event evento com erro
     * @param error exceção ocorrida
     */
    @Transactional
    protected void handlePublishError(OutboxEvent event, Throwable error) {
        if (event.getRetryCount() >= maxRetries) {
            // Max retries atingido → marcar como FAILED
            log.error("Outbox event FAILED after {} retries: id={}, type={}",
                    maxRetries, event.getId(), event.getEventType());
            outboxService.markAsFailed(event.getId(), error.getMessage());
        } else {
            // Incrementar retry count
            log.warn("Outbox event publish error, will retry: id={}, retryCount={}/{}",
                    event.getId(), event.getRetryCount() + 1, maxRetries);
            outboxService.recordError(event.getId(), error.getMessage());
        }
    }

    /**
     * Desserializa o payload JSON para o tipo correto baseado no eventType.
     *
     * IMPORTANTE: Mapeia eventType para a classe correspondente.
     * Adicione novos mapeamentos aqui conforme novos tipos de eventos forem criados.
     *
     * @param outboxEvent evento do outbox
     * @return payload deserializado como o tipo correto
     * @throws Exception se falhar ao deserializar
     */
    private Object deserializePayload(OutboxEvent outboxEvent) throws Exception {
        return switch (outboxEvent.getEventType()) {
            case "PAYMENT_APPROVED" -> objectMapper.readValue(
                    outboxEvent.getPayload(),
                    PaymentApprovedEvent.class
            );
            // Adicione novos tipos aqui conforme necessário
            // case "PAYMENT_CANCELLED" -> objectMapper.readValue(...)
            default -> throw new IllegalArgumentException(
                    "Unknown event type: " + outboxEvent.getEventType()
            );
        };
    }

    /**
     * Extrai o eventId do evento de domínio deserializado.
     *
     * IMPORTANTE: Este é o eventId usado para idempotência no consumer.
     * Cada tipo de evento deve retornar seu eventId específico.
     *
     * @param payload evento deserializado
     * @param eventType tipo do evento
     * @return eventId do evento de domínio
     */
    private String extractEventId(Object payload, String eventType) {
        return switch (eventType) {
            case "PAYMENT_APPROVED" -> ((PaymentApprovedEvent) payload).eventId();
            // Adicione novos tipos aqui conforme necessário
            // case "PAYMENT_CANCELLED" -> ((PaymentCancelledEvent) payload).eventId();
            default -> throw new IllegalArgumentException(
                    "Unknown event type for eventId extraction: " + eventType
            );
        };
    }

    /**
     * Retorna métricas de eventos pendentes (para monitoring).
     *
     * @return quantidade de eventos pendentes
     */
    public long getPendingEventsCount() {
        return outboxService.countByStatus(OutboxEventStatus.PENDING);
    }

    /**
     * Retorna métricas de eventos falhados (para alertas).
     *
     * @return quantidade de eventos falhados
     */
    public long getFailedEventsCount() {
        return outboxService.countByStatus(OutboxEventStatus.FAILED);
    }
}
