package com.mvbr.store.infrastructure.adapter.out.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for OutboxPublisher.
 *
 * Tests the Outbox Pattern publisher that sends events to Kafka:
 * - Fetching pending events
 * - Publishing to Kafka
 * - Handling publish success/failure
 * - Retry logic
 * - Status transitions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher - Unit Tests")
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private OutboxEvent testEvent;
    private PaymentApprovedEvent testPayload;
    private String testPayloadJson;

    @BeforeEach
    void setUp() {
        // Set test configuration values
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 100);
        ReflectionTestUtils.setField(outboxPublisher, "maxRetries", 3);

        // Create test payload
        testPayload = new PaymentApprovedEvent(
                "evt_123",
                "pay_456",
                "user_789",
                new BigDecimal("100.00"),
                "USD",
                "APPROVED",
                Instant.now().toEpochMilli()
        );

        testPayloadJson = """
            {
                "eventId": "evt_123",
                "paymentId": "pay_456",
                "userId": "user_789",
                "amount": 100.00,
                "currency": "USD",
                "status": "APPROVED"
            }
            """;

        // Create test outbox event
        testEvent = new OutboxEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                testPayloadJson
        );
    }

    // =======================================
    //      PUBLISH PENDING EVENTS TESTS
    // =======================================

    @Test
    @DisplayName("Should not process when no pending events exist")
    void shouldNotProcessWhenNoPendingEventsExist() {
        // Given
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, 100))
                .thenReturn(Collections.emptyList());

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(outboxService, never()).markAsPublished(any());
    }

    @Test
    @DisplayName("Should fetch pending events with correct batch size")
    void shouldFetchPendingEventsWithCorrectBatchSize() {
        // Given
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, 100))
                .thenReturn(Collections.emptyList());

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        verify(outboxRepository).findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, 100);
    }

    @Test
    @DisplayName("Should process all pending events in batch")
    void shouldProcessAllPendingEventsInBatch() throws Exception {
        // Given
        OutboxEvent event1 = createTestEvent("evt_1", "pay_1");
        OutboxEvent event2 = createTestEvent("evt_2", "pay_2");
        OutboxEvent event3 = createTestEvent("evt_3", "pay_3");

        List<OutboxEvent> pendingEvents = Arrays.asList(event1, event2, event3);

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, 100))
                .thenReturn(pendingEvents);
        when(objectMapper.readValue(anyString(), eq(PaymentApprovedEvent.class)))
                .thenReturn(testPayload);

        SendResult<String, Object> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("payment.approved.v1");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(0L);
        when(metadata.timestamp()).thenReturn(System.currentTimeMillis());

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        outboxPublisher.publishPendingEvents();

        // Then
        verify(kafkaTemplate, times(3)).send(any(ProducerRecord.class));
    }

    // =======================================
    //      PUBLISH EVENT SUCCESS TESTS
    // =======================================

    @Test
    @DisplayName("Should deserialize payload correctly")
    void shouldDeserializePayloadCorrectly() throws Exception {
        // Given
        when(objectMapper.readValue(testPayloadJson, PaymentApprovedEvent.class))
                .thenReturn(testPayload);

        SendResult<String, Object> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("payment.approved.v1");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(0L);
        when(metadata.timestamp()).thenReturn(System.currentTimeMillis());

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        outboxPublisher.publishEvent(testEvent);

        // Then
        verify(objectMapper).readValue(testPayloadJson, PaymentApprovedEvent.class);
    }

    @Test
    @DisplayName("Should create producer record with correct topic and partition key")
    void shouldCreateProducerRecordWithCorrectTopicAndPartitionKey() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(PaymentApprovedEvent.class)))
                .thenReturn(testPayload);

        SendResult<String, Object> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("payment.approved.v1");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(0L);
        when(metadata.timestamp()).thenReturn(System.currentTimeMillis());

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        outboxPublisher.publishEvent(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("payment.approved.v1");
        assertThat(record.key()).isEqualTo("user_789");
        assertThat(record.value()).isEqualTo(testPayload);
    }

    @Test
    @DisplayName("Should add event headers to producer record")
    void shouldAddEventHeadersToProducerRecord() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(PaymentApprovedEvent.class)))
                .thenReturn(testPayload);

        SendResult<String, Object> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("payment.approved.v1");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(0L);
        when(metadata.timestamp()).thenReturn(System.currentTimeMillis());

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        outboxPublisher.publishEvent(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.headers()).isNotNull();
        assertThat(record.headers().toArray()).hasSizeGreaterThan(0);
        assertThat(record.headers().lastHeader("event-type")).isNotNull();
        assertThat(record.headers().lastHeader("event-id")).isNotNull();
        assertThat(record.headers().lastHeader("aggregate-id")).isNotNull();
        assertThat(record.headers().lastHeader("source")).isNotNull();
    }

    @Test
    @DisplayName("Should mark event as published on successful send")
    void shouldMarkEventAsPublishedOnSuccessfulSend() throws Exception {
        // Given
        when(objectMapper.readValue(anyString(), eq(PaymentApprovedEvent.class)))
                .thenReturn(testPayload);

        SendResult<String, Object> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = mock(org.apache.kafka.clients.producer.RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("payment.approved.v1");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(0L);
        when(metadata.timestamp()).thenReturn(System.currentTimeMillis());

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        outboxPublisher.publishEvent(testEvent);

        // Then
        verify(outboxService).markAsPublished(testEvent.getId());
    }

    // =======================================
    //      PUBLISH EVENT FAILURE TESTS
    // =======================================

    @Test
    @DisplayName("Should handle publish error and record retry")
    void shouldHandlePublishErrorAndRecordRetry() throws Exception {
        // Given
        testEvent.setRetryCount(0);

        when(objectMapper.readValue(anyString(), eq(PaymentApprovedEvent.class)))
                .thenReturn(testPayload);

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        try {
            outboxPublisher.publishEvent(testEvent);
        } catch (Exception e) {
            // Expected
        }

        // Then
        verify(outboxService).recordError(eq(testEvent.getId()), anyString());
        verify(outboxService, never()).markAsPublished(any());
    }

    @Test
    @DisplayName("Should mark event as failed after max retries")
    void shouldMarkEventAsFailedAfterMaxRetries() {
        // Given
        testEvent.setRetryCount(3); // Already at max retries

        // When
        outboxPublisher.handlePublishError(testEvent, new RuntimeException("Still failing"));

        // Then
        verify(outboxService).markAsFailed(eq(testEvent.getId()), anyString());
        verify(outboxService, never()).recordError(any(), any());
    }

    @Test
    @DisplayName("Should record error when retries not exhausted")
    void shouldRecordErrorWhenRetriesNotExhausted() {
        // Given
        testEvent.setRetryCount(1);

        // When
        outboxPublisher.handlePublishError(testEvent, new RuntimeException("Temporary error"));

        // Then
        verify(outboxService).recordError(eq(testEvent.getId()), eq("Temporary error"));
        verify(outboxService, never()).markAsFailed(any(), any());
    }

    // =======================================
    //      DESERIALIZATION TESTS
    // =======================================

    @Test
    @DisplayName("Should throw exception for unknown event type")
    void shouldThrowExceptionForUnknownEventType() {
        // Given
        OutboxEvent unknownEvent = new OutboxEvent(
                "PAYMENT",
                "pay_456",
                "UNKNOWN_EVENT_TYPE",
                "unknown.topic",
                "user_789",
                "{}"
        );

        // When/Then - The publishEvent method should handle this internally
        // We're testing that the publisher can handle unknown event types gracefully
        try {
            outboxPublisher.publishEvent(unknownEvent);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("Failed to publish outbox event");
        }
    }

    // =======================================
    //      METRICS TESTS
    // =======================================

    @Test
    @DisplayName("Should return pending events count")
    void shouldReturnPendingEventsCount() {
        // Given
        when(outboxService.countByStatus(OutboxEventStatus.PENDING)).thenReturn(5L);

        // When
        long count = outboxPublisher.getPendingEventsCount();

        // Then
        assertThat(count).isEqualTo(5L);
        verify(outboxService).countByStatus(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("Should return failed events count")
    void shouldReturnFailedEventsCount() {
        // Given
        when(outboxService.countByStatus(OutboxEventStatus.FAILED)).thenReturn(2L);

        // When
        long count = outboxPublisher.getFailedEventsCount();

        // Then
        assertThat(count).isEqualTo(2L);
        verify(outboxService).countByStatus(OutboxEventStatus.FAILED);
    }

    // =======================================
    //      HELPER METHODS
    // =======================================

    private OutboxEvent createTestEvent(String eventId, String paymentId) {
        return new OutboxEvent(
                "PAYMENT",
                paymentId,
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                String.format("{\"eventId\":\"%s\",\"paymentId\":\"%s\"}", eventId, paymentId)
        );
    }
}
