package com.mvbr.store.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity to track processed Kafka events for idempotency.
 * Prevents duplicate processing of the same event (e.g., due to retries, crashes, rebalancing).
 */
@Entity
@Table(
    name = "processed_events",
    indexes = {
        @Index(name = "idx_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_processed_at", columnList = "processed_at")
    }
)
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique event identifier (from Kafka event payload).
     * This is the key field for idempotency checks.
     */
    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    /**
     * Kafka topic from which the event was consumed.
     */
    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    /**
     * Event type (e.g., "PAYMENT_APPROVED", "ORDER_CREATED").
     */
    @Column(name = "event_type", length = 100)
    private String eventType;

    /**
     * Timestamp when the event was processed.
     */
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /**
     * Optional: Kafka partition (for debugging/tracing).
     */
    @Column(name = "partition")
    private Integer partition;

    /**
     * Optional: Kafka offset (for debugging/tracing).
     */
    @Column(name = "offset")
    private Long offset;

    // =============================
    // Constructors
    // =============================

    public ProcessedEvent() {
        // JPA requires default constructor
    }

    public ProcessedEvent(String eventId, String topic, String eventType) {
        this.eventId = eventId;
        this.topic = topic;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    public ProcessedEvent(String eventId, String topic, String eventType, Integer partition, Long offset) {
        this.eventId = eventId;
        this.topic = topic;
        this.eventType = eventType;
        this.partition = partition;
        this.offset = offset;
        this.processedAt = Instant.now();
    }

    // =============================
    // Getters and Setters
    // =============================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Integer getPartition() {
        return partition;
    }

    public void setPartition(Integer partition) {
        this.partition = partition;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    @Override
    public String toString() {
        return "ProcessedEvent{" +
                "id=" + id +
                ", eventId='" + eventId + '\'' +
                ", topic='" + topic + '\'' +
                ", eventType='" + eventType + '\'' +
                ", processedAt=" + processedAt +
                ", partition=" + partition +
                ", offset=" + offset +
                '}';
    }
}