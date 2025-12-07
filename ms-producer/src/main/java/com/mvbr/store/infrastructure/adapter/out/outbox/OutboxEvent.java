package com.mvbr.store.infrastructure.adapter.out.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * OutboxEvent - Entidade JPA para Outbox Pattern.
 *
 * PROPÓSITO:
 * Armazena eventos que precisam ser publicados no Kafka de forma assíncrona.
 * Garante consistência entre banco de dados e mensageria.
 *
 * OUTBOX PATTERN:
 * 1. Transação salva entidade de negócio (Payment) + OutboxEvent
 * 2. Job assíncrono busca eventos PENDING
 * 3. Publica no Kafka
 * 4. Marca como PUBLISHED
 *
 * VANTAGENS:
 * - Atomicidade (banco + evento na mesma transação)
 * - Resiliência (retry automático)
 * - Rastreabilidade (histórico de eventos)
 * - At-least-once delivery garantido
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "aggregate_type", length = 50, nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 36, nullable = false)
    private String aggregateId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "topic", length = 100, nullable = false)
    private String topic;

    @Column(name = "partition_key", length = 100, nullable = false)
    private String partitionKey;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "version", nullable = false)
    private Integer version;

    // ========== CONSTRUTORES ==========

    /**
     * Construtor padrão (JPA exige).
     */
    protected OutboxEvent() {
    }

    /**
     * Construtor para criar novo evento outbox.
     *
     * @param aggregateType tipo do agregado (PAYMENT, ORDER, etc)
     * @param aggregateId ID da entidade de negócio
     * @param eventType tipo do evento (PAYMENT_APPROVED, etc)
     * @param topic tópico Kafka de destino
     * @param partitionKey chave de particionamento
     * @param payload JSON do evento serializado
     */
    public OutboxEvent(String aggregateType,
                       String aggregateId,
                       String eventType,
                       String topic,
                       String partitionKey,
                       String payload) {
        this.id = UUID.randomUUID().toString();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.version = 1;
    }

    // ========== MÉTODOS DE NEGÓCIO ==========

    /**
     * Marca evento como publicado com sucesso.
     */
    public void markAsPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /**
     * Marca evento como falhado após múltiplas tentativas.
     *
     * @param errorMessage mensagem de erro
     */
    public void markAsFailed(String errorMessage) {
        this.status = OutboxEventStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Incrementa contador de retry.
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Registra erro de tentativa de publicação.
     *
     * @param errorMessage mensagem de erro
     */
    public void recordError(String errorMessage) {
        this.errorMessage = errorMessage;
        incrementRetryCount();
    }

    /**
     * Verifica se evento está pendente.
     */
    public boolean isPending() {
        return this.status == OutboxEventStatus.PENDING;
    }

    /**
     * Verifica se evento foi publicado.
     */
    public boolean isPublished() {
        return this.status == OutboxEventStatus.PUBLISHED;
    }

    /**
     * Verifica se evento falhou.
     */
    public boolean isFailed() {
        return this.status == OutboxEventStatus.FAILED;
    }

    // ========== GETTERS E SETTERS ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxEventStatus status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "id='" + id + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", topic='" + topic + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", createdAt=" + createdAt +
                ", publishedAt=" + publishedAt +
                '}';
    }
}
