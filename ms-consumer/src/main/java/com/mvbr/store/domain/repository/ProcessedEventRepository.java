package com.mvbr.store.domain.repository;

import com.mvbr.store.domain.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for tracking processed Kafka events (idempotency).
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    /**
     * Check if an event has already been processed (idempotency check).
     *
     * @param eventId unique event identifier
     * @return true if event was already processed, false otherwise
     */
    boolean existsByEventId(String eventId);

    /**
     * Find a processed event by its eventId.
     *
     * @param eventId unique event identifier
     * @return Optional containing the processed event if found
     */
    Optional<ProcessedEvent> findByEventId(String eventId);

    /**
     * Find all processed events for a specific topic.
     * Useful for monitoring and debugging.
     *
     * @param topic Kafka topic name
     * @return list of processed events from that topic
     */
    List<ProcessedEvent> findByTopic(String topic);

    /**
     * Find all processed events of a specific type.
     * Useful for monitoring and debugging.
     *
     * @param eventType event type (e.g., "PAYMENT_APPROVED")
     * @return list of processed events of that type
     */
    List<ProcessedEvent> findByEventType(String eventType);

    /**
     * Delete processed events older than a certain date.
     * Useful for data retention policies (e.g., delete events older than 90 days).
     *
     * @param cutoffDate events processed before this date will be deleted
     * @return number of deleted records
     */
    long deleteByProcessedAtBefore(Instant cutoffDate);
}