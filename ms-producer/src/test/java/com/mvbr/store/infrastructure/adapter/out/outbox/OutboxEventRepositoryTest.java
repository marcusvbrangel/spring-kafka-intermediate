package com.mvbr.store.infrastructure.adapter.out.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository Tests for OutboxEventRepository.
 *
 * Tests all custom query methods for the Outbox Pattern implementation:
 * - findByStatusOrderByCreatedAtAsc (with limit)
 * - findByStatus
 * - findByStatusAndPublishedAtBefore
 * - findByStatusAndRetryCountGreaterThan
 * - countByStatus
 * - findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("OutboxEventRepository - Persistence Tests")
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        repository.deleteAll();
        entityManager.flush();
    }

    // =======================================
    //      FIND BY STATUS WITH ORDERING
    // =======================================

    @Test
    @DisplayName("Should find pending events ordered by creation date")
    void shouldFindPendingEventsOrderedByCreatedAt() {
        // Given - Create events with different timestamps
        OutboxEvent event1 = createOutboxEvent("pay_1", OutboxEventStatus.PENDING);
        sleepMillis(10);
        OutboxEvent event2 = createOutboxEvent("pay_2", OutboxEventStatus.PENDING);
        sleepMillis(10);
        OutboxEvent event3 = createOutboxEvent("pay_3", OutboxEventStatus.PENDING);

        repository.save(event1);
        repository.save(event2);
        repository.save(event3);
        entityManager.flush();

        // When
        List<OutboxEvent> events = repository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                10
        );

        // Then
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getAggregateId()).isEqualTo("pay_1");
        assertThat(events.get(1).getAggregateId()).isEqualTo("pay_2");
        assertThat(events.get(2).getAggregateId()).isEqualTo("pay_3");
    }

    @Test
    @DisplayName("Should respect limit when finding events by status")
    void shouldRespectLimitWhenFindingEventsByStatus() {
        // Given - Create 5 pending events
        for (int i = 1; i <= 5; i++) {
            repository.save(createOutboxEvent("pay_" + i, OutboxEventStatus.PENDING));
            sleepMillis(5);
        }
        entityManager.flush();

        // When - Limit to 3
        List<OutboxEvent> events = repository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                3
        );

        // Then
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getAggregateId()).isEqualTo("pay_1");
        assertThat(events.get(1).getAggregateId()).isEqualTo("pay_2");
        assertThat(events.get(2).getAggregateId()).isEqualTo("pay_3");
    }

    @Test
    @DisplayName("Should return empty list when no pending events exist")
    void shouldReturnEmptyListWhenNoPendingEventsExist() {
        // Given - Only published events
        repository.save(createOutboxEvent("pay_1", OutboxEventStatus.PUBLISHED));
        repository.save(createOutboxEvent("pay_2", OutboxEventStatus.FAILED));
        entityManager.flush();

        // When
        List<OutboxEvent> events = repository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                10
        );

        // Then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should not include events with different status")
    void shouldNotIncludeEventsWithDifferentStatus() {
        // Given
        repository.save(createOutboxEvent("pay_pending", OutboxEventStatus.PENDING));
        repository.save(createOutboxEvent("pay_published", OutboxEventStatus.PUBLISHED));
        repository.save(createOutboxEvent("pay_failed", OutboxEventStatus.FAILED));
        entityManager.flush();

        // When
        List<OutboxEvent> pendingEvents = repository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                10
        );

        // Then
        assertThat(pendingEvents).hasSize(1);
        assertThat(pendingEvents.get(0).getAggregateId()).isEqualTo("pay_pending");
    }

    // =======================================
    //      FIND BY STATUS (NO LIMIT)
    // =======================================

    @Test
    @DisplayName("Should find all events by status without limit")
    void shouldFindAllEventsByStatusWithoutLimit() {
        // Given
        repository.save(createOutboxEvent("pay_1", OutboxEventStatus.PENDING));
        repository.save(createOutboxEvent("pay_2", OutboxEventStatus.PENDING));
        repository.save(createOutboxEvent("pay_3", OutboxEventStatus.PUBLISHED));
        entityManager.flush();

        // When
        List<OutboxEvent> pendingEvents = repository.findByStatus(OutboxEventStatus.PENDING);

        // Then
        assertThat(pendingEvents).hasSize(2);
        assertThat(pendingEvents)
                .extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder("pay_1", "pay_2");
    }

    @Test
    @DisplayName("Should find all published events")
    void shouldFindAllPublishedEvents() {
        // Given
        OutboxEvent event1 = createOutboxEvent("pay_1", OutboxEventStatus.PUBLISHED);
        OutboxEvent event2 = createOutboxEvent("pay_2", OutboxEventStatus.PUBLISHED);
        OutboxEvent event3 = createOutboxEvent("pay_3", OutboxEventStatus.PENDING);

        repository.save(event1);
        repository.save(event2);
        repository.save(event3);
        entityManager.flush();

        // When
        List<OutboxEvent> publishedEvents = repository.findByStatus(OutboxEventStatus.PUBLISHED);

        // Then
        assertThat(publishedEvents).hasSize(2);
        assertThat(publishedEvents)
                .extracting(OutboxEvent::getStatus)
                .containsOnly(OutboxEventStatus.PUBLISHED);
    }

    // =======================================
    //      FIND BY STATUS AND PUBLISHED DATE
    // =======================================

    @Test
    @DisplayName("Should find old published events for cleanup")
    void shouldFindOldPublishedEventsForCleanup() {
        // Given
        Instant now = Instant.now();
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant tenDaysAgo = now.minus(10, ChronoUnit.DAYS);

        // Old event (10 days ago)
        OutboxEvent oldEvent = createOutboxEvent("pay_old", OutboxEventStatus.PUBLISHED);
        oldEvent.setPublishedAt(tenDaysAgo);

        // Recent event (2 days ago)
        OutboxEvent recentEvent = createOutboxEvent("pay_recent", OutboxEventStatus.PUBLISHED);
        recentEvent.setPublishedAt(twoDaysAgo);

        repository.save(oldEvent);
        repository.save(recentEvent);
        entityManager.flush();

        // When - Find events published before 7 days ago
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        List<OutboxEvent> oldEvents = repository.findByStatusAndPublishedAtBefore(
                OutboxEventStatus.PUBLISHED,
                sevenDaysAgo
        );

        // Then
        assertThat(oldEvents).hasSize(1);
        assertThat(oldEvents.get(0).getAggregateId()).isEqualTo("pay_old");
    }

    @Test
    @DisplayName("Should return empty list when no old published events exist")
    void shouldReturnEmptyListWhenNoOldPublishedEventsExist() {
        // Given - All events are recent
        Instant now = Instant.now();
        OutboxEvent event = createOutboxEvent("pay_recent", OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(now.minus(1, ChronoUnit.DAYS));
        repository.save(event);
        entityManager.flush();

        // When - Look for events older than 7 days
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        List<OutboxEvent> oldEvents = repository.findByStatusAndPublishedAtBefore(
                OutboxEventStatus.PUBLISHED,
                sevenDaysAgo
        );

        // Then
        assertThat(oldEvents).isEmpty();
    }

    // =======================================
    //      FIND FAILED EVENTS
    // =======================================

    @Test
    @DisplayName("Should find failed events with retry count greater than threshold")
    void shouldFindFailedEventsWithHighRetryCount() {
        // Given
        OutboxEvent event1 = createOutboxEvent("pay_1", OutboxEventStatus.FAILED);
        event1.setRetryCount(1);

        OutboxEvent event2 = createOutboxEvent("pay_2", OutboxEventStatus.FAILED);
        event2.setRetryCount(3);

        OutboxEvent event3 = createOutboxEvent("pay_3", OutboxEventStatus.FAILED);
        event3.setRetryCount(5);

        repository.save(event1);
        repository.save(event2);
        repository.save(event3);
        entityManager.flush();

        // When - Find events with retry count > 2
        List<OutboxEvent> failedEvents = repository.findByStatusAndRetryCountGreaterThan(
                OutboxEventStatus.FAILED,
                2
        );

        // Then
        assertThat(failedEvents).hasSize(2);
        assertThat(failedEvents)
                .extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder("pay_2", "pay_3");
    }

    @Test
    @DisplayName("Should return empty list when no failed events exceed retry threshold")
    void shouldReturnEmptyListWhenNoFailedEventsExceedRetryThreshold() {
        // Given
        OutboxEvent event = createOutboxEvent("pay_1", OutboxEventStatus.FAILED);
        event.setRetryCount(1);
        repository.save(event);
        entityManager.flush();

        // When - Look for events with retry > 5
        List<OutboxEvent> failedEvents = repository.findByStatusAndRetryCountGreaterThan(
                OutboxEventStatus.FAILED,
                5
        );

        // Then
        assertThat(failedEvents).isEmpty();
    }

    // =======================================
    //      COUNT BY STATUS
    // =======================================

    @Test
    @DisplayName("Should count events by status")
    void shouldCountEventsByStatus() {
        // Given
        repository.save(createOutboxEvent("pay_1", OutboxEventStatus.PENDING));
        repository.save(createOutboxEvent("pay_2", OutboxEventStatus.PENDING));
        repository.save(createOutboxEvent("pay_3", OutboxEventStatus.PENDING));
        repository.save(createOutboxEvent("pay_4", OutboxEventStatus.PUBLISHED));
        repository.save(createOutboxEvent("pay_5", OutboxEventStatus.PUBLISHED));
        repository.save(createOutboxEvent("pay_6", OutboxEventStatus.FAILED));
        entityManager.flush();

        // When/Then
        assertThat(repository.countByStatus(OutboxEventStatus.PENDING)).isEqualTo(3);
        assertThat(repository.countByStatus(OutboxEventStatus.PUBLISHED)).isEqualTo(2);
        assertThat(repository.countByStatus(OutboxEventStatus.FAILED)).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return zero when no events exist for status")
    void shouldReturnZeroWhenNoEventsExistForStatus() {
        // Given - No events at all

        // When
        long count = repository.countByStatus(OutboxEventStatus.PENDING);

        // Then
        assertThat(count).isZero();
    }

    // =======================================
    //      FIND BY AGGREGATE
    // =======================================

    @Test
    @DisplayName("Should find events by aggregate type and ID ordered by creation date descending")
    void shouldFindEventsByAggregateTypeAndId() {
        // Given
        OutboxEvent event1 = createOutboxEventForAggregate("PAYMENT", "pay_123");
        sleepMillis(10);
        OutboxEvent event2 = createOutboxEventForAggregate("PAYMENT", "pay_123");
        sleepMillis(10);
        OutboxEvent event3 = createOutboxEventForAggregate("PAYMENT", "pay_123");

        // Different aggregate
        OutboxEvent event4 = createOutboxEventForAggregate("PAYMENT", "pay_456");

        repository.save(event1);
        repository.save(event2);
        repository.save(event3);
        repository.save(event4);
        entityManager.flush();

        // When
        List<OutboxEvent> events = repository.findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
                "PAYMENT",
                "pay_123"
        );

        // Then
        assertThat(events).hasSize(3);
        // Should be in descending order (newest first)
        assertThat(events.get(0).getCreatedAt())
                .isAfter(events.get(1).getCreatedAt());
        assertThat(events.get(1).getCreatedAt())
                .isAfter(events.get(2).getCreatedAt());
    }

    @Test
    @DisplayName("Should return empty list when no events exist for aggregate")
    void shouldReturnEmptyListWhenNoEventsExistForAggregate() {
        // Given
        repository.save(createOutboxEventForAggregate("PAYMENT", "pay_123"));
        entityManager.flush();

        // When
        List<OutboxEvent> events = repository.findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
                "PAYMENT",
                "pay_999"
        );

        // Then
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Should separate events by aggregate type")
    void shouldSeparateEventsByAggregateType() {
        // Given
        repository.save(createOutboxEventForAggregate("PAYMENT", "pay_123"));
        repository.save(createOutboxEventForAggregate("ORDER", "pay_123"));
        entityManager.flush();

        // When
        List<OutboxEvent> paymentEvents = repository.findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
                "PAYMENT",
                "pay_123"
        );
        List<OutboxEvent> orderEvents = repository.findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
                "ORDER",
                "pay_123"
        );

        // Then
        assertThat(paymentEvents).hasSize(1);
        assertThat(orderEvents).hasSize(1);
        assertThat(paymentEvents.get(0).getAggregateType()).isEqualTo("PAYMENT");
        assertThat(orderEvents.get(0).getAggregateType()).isEqualTo("ORDER");
    }

    // =======================================
    //      BASIC CRUD OPERATIONS
    // =======================================

    @Test
    @DisplayName("Should save outbox event successfully")
    void shouldSaveOutboxEventSuccessfully() {
        // Given
        OutboxEvent event = createOutboxEvent("pay_1", OutboxEventStatus.PENDING);

        // When
        OutboxEvent saved = repository.save(event);
        entityManager.flush();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateId()).isEqualTo("pay_1");
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update outbox event status")
    void shouldUpdateOutboxEventStatus() {
        // Given
        OutboxEvent event = createOutboxEvent("pay_1", OutboxEventStatus.PENDING);
        repository.save(event);
        entityManager.flush();

        // When - Mark as published
        event.markAsPublished();
        repository.save(event);
        entityManager.flush();
        entityManager.clear();

        // Then
        OutboxEvent updated = repository.findById(event.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(updated.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should increment retry count")
    void shouldIncrementRetryCount() {
        // Given
        OutboxEvent event = createOutboxEvent("pay_1", OutboxEventStatus.PENDING);
        repository.save(event);
        entityManager.flush();

        // When
        event.recordError("Connection timeout");
        repository.save(event);
        entityManager.flush();
        entityManager.clear();

        // Then
        OutboxEvent updated = repository.findById(event.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getErrorMessage()).isEqualTo("Connection timeout");
    }

    // =======================================
    //      HELPER METHODS
    // =======================================

    private OutboxEvent createOutboxEvent(String aggregateId, OutboxEventStatus status) {
        OutboxEvent event = new OutboxEvent(
                "PAYMENT",
                aggregateId,
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_123",
                "{\"paymentId\": \"" + aggregateId + "\"}"
        );
        event.setStatus(status);
        return event;
    }

    private OutboxEvent createOutboxEventForAggregate(String aggregateType, String aggregateId) {
        return new OutboxEvent(
                aggregateType,
                aggregateId,
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_123",
                "{\"paymentId\": \"" + aggregateId + "\"}"
        );
    }

    private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
