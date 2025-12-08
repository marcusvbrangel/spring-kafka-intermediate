package com.mvbr.store.infrastructure.adapter.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OutboxService - Serviço para gerenciar eventos Outbox.
 *
 * RESPONSABILIDADES:
 * 1. Persistir eventos na tabela outbox (dentro da mesma transação do negócio)
 * 2. Serializar payloads para JSON
 * 3. Validar eventos antes de salvar
 *
 * IMPORTANTE:
 * - Métodos @Transactional para garantir atomicidade
 * - Sempre chamado DENTRO de uma transação existente
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Salva evento no outbox (dentro da transação atual).
     *
     * CRITICAL:
     * Este método DEVE ser chamado dentro de uma transação @Transactional
     * para garantir atomicidade com a operação de negócio.
     *
     * @param aggregateType tipo do agregado (ex: "PAYMENT")
     * @param aggregateId ID da entidade
     * @param eventType tipo do evento (ex: "PAYMENT_APPROVED")
     * @param topic tópico Kafka
     * @param partitionKey chave de particionamento
     * @param eventPayload objeto do evento (será serializado para JSON)
     * @return evento salvo
     */
    @Transactional
    public OutboxEvent saveEvent(String aggregateType,
                                  String aggregateId,
                                  String eventType,
                                  String topic,
                                  String partitionKey,
                                  Object eventPayload) {

        try {
            // Serializar payload para JSON
            String payloadJson = objectMapper.writeValueAsString(eventPayload);

            // Criar evento outbox
            OutboxEvent outboxEvent = new OutboxEvent(
                    aggregateType,
                    aggregateId,
                    eventType,
                    topic,
                    partitionKey,
                    payloadJson
            );

            // Salvar na mesma transação
            OutboxEvent saved = outboxRepository.save(outboxEvent);

            // =============================
            // LOG: EVENTO SALVO NO OUTBOX
            // =============================
            log.info("\n" +
                    "=================================================================\n" +
                    "                   💾 OUTBOX EVENT SAVED                         \n" +
                    "=================================================================\n" +
                    "  Outbox ID:       {}\n" +
                    "  Aggregate Type:  {}\n" +
                    "  Aggregate ID:    {}\n" +
                    "  Event Type:      {}\n" +
                    "  Topic:           {}\n" +
                    "  Partition Key:   {}\n" +
                    "  Status:          {}\n" +
                    "  Created At:      {}\n" +
                    "=================================================================",
                    saved.getId(),
                    aggregateType,
                    aggregateId,
                    eventType,
                    topic,
                    partitionKey,
                    saved.getStatus(),
                    saved.getCreatedAt()
            );

            return saved;

        } catch (JsonProcessingException e) {
            // =============================
            // LOG: ERRO DE SERIALIZAÇÃO
            // =============================
            log.error("\n" +
                    "=================================================================\n" +
                    "                  ❌ OUTBOX SERIALIZATION ERROR                  \n" +
                    "=================================================================\n" +
                    "  Aggregate Type:  {}\n" +
                    "  Aggregate ID:    {}\n" +
                    "  Event Type:      {}\n" +
                    "  Error:           {}\n" +
                    "=================================================================",
                    aggregateType,
                    aggregateId,
                    eventType,
                    e.getMessage()
            );
            throw new OutboxSerializationException(
                    "Failed to serialize event payload: " + eventType, e);
        }
    }

    /**
     * Busca evento por ID.
     *
     * @param eventId ID do evento
     * @return evento ou null
     */
    public OutboxEvent findById(String eventId) {
        return outboxRepository.findById(eventId).orElse(null);
    }

    /**
     * Marca evento como publicado.
     *
     * @param eventId ID do evento
     */
    @Transactional
    public void markAsPublished(String eventId) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.markAsPublished();
            outboxRepository.save(event);
            log.info("Outbox event marked as PUBLISHED: id={}, type={}",
                    eventId, event.getEventType());
        });
    }

    /**
     * Marca evento como falhado.
     *
     * @param eventId ID do evento
     * @param errorMessage mensagem de erro
     */
    @Transactional
    public void markAsFailed(String eventId, String errorMessage) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.markAsFailed(errorMessage);
            outboxRepository.save(event);
            log.error("Outbox event marked as FAILED: id={}, type={}, error={}",
                    eventId, event.getEventType(), errorMessage);
        });
    }

    /**
     * Registra erro de tentativa de publicação.
     *
     * @param eventId ID do evento
     * @param errorMessage mensagem de erro
     */
    @Transactional
    public void recordError(String eventId, String errorMessage) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.recordError(errorMessage);
            outboxRepository.save(event);
            log.warn("Outbox event error recorded: id={}, retryCount={}, error={}",
                    eventId, event.getRetryCount(), errorMessage);
        });
    }

    /**
     * Retorna métricas de eventos por status.
     *
     * @param status status
     * @return quantidade
     */
    public long countByStatus(OutboxEventStatus status) {
        return outboxRepository.countByStatus(status);
    }

    /**
     * Exceção customizada para erros de serialização.
     */
    public static class OutboxSerializationException extends RuntimeException {
        public OutboxSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
