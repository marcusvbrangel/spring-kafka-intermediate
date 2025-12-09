package com.mvbr.store.infrastructure.adapter.out.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for OutboxService.
 *
 * Tests the Outbox Pattern implementation for saving events:
 * - Event serialization
 * - Event persistence
 * - Status transitions (PENDING → PUBLISHED/FAILED)
 * - Error handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxService - Unit Tests")
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    private PaymentApprovedEvent testEvent;
    private OutboxEvent testOutboxEvent;

    @BeforeEach
    void setUp() {
        testEvent = new PaymentApprovedEvent(
                "evt_123",
                "pay_456",
                "user_789",
                new BigDecimal("100.00"),
                "USD",
                "APPROVED",
                Instant.now().toEpochMilli()
        );

        testOutboxEvent = new OutboxEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                "{\"eventId\":\"evt_123\"}"
        );
    }

    // =======================================
    //      SAVE EVENT TESTS
    // =======================================

    @Test
    @DisplayName("Should save event to outbox successfully")
    void shouldSaveEventToOutboxSuccessfully() throws Exception {
        // Given
        String expectedJson = "{\"eventId\":\"evt_123\",\"paymentId\":\"pay_456\"}";
        when(objectMapper.writeValueAsString(testEvent)).thenReturn(expectedJson);
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(testOutboxEvent);

        // When
        OutboxEvent result = outboxService.saveEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                testEvent
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(result.getAggregateId()).isEqualTo("pay_456");
        assertThat(result.getEventType()).isEqualTo("PAYMENT_APPROVED");
        assertThat(result.getTopic()).isEqualTo("payment.approved.v1");
        assertThat(result.getPartitionKey()).isEqualTo("user_789");

        // Verify serialization was called
        verify(objectMapper).writeValueAsString(testEvent);

        // Verify repository save was called
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());

        OutboxEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(capturedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(capturedEvent.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("Should serialize event payload to JSON")
    void shouldSerializeEventPayloadToJson() throws Exception {
        // Given
        String expectedJson = "{\"eventId\":\"evt_123\",\"paymentId\":\"pay_456\",\"amount\":100.00}";
        when(objectMapper.writeValueAsString(testEvent)).thenReturn(expectedJson);
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            assertThat(event.getPayload()).isEqualTo(expectedJson);
            return event;
        });

        // When
        outboxService.saveEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                testEvent
        );

        // Then
        verify(objectMapper).writeValueAsString(testEvent);
    }

    @Test
    @DisplayName("Should throw exception when serialization fails")
    void shouldThrowExceptionWhenSerializationFails() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialization error") {});

        // When/Then
        assertThatThrownBy(() ->
                outboxService.saveEvent(
                        "PAYMENT",
                        "pay_456",
                        "PAYMENT_APPROVED",
                        "payment.approved.v1",
                        "user_789",
                        testEvent
                )
        )
                .isInstanceOf(OutboxService.OutboxSerializationException.class)
                .hasMessageContaining("Failed to serialize event payload")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);

        // Verify repository was never called
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should create outbox event with PENDING status")
    void shouldCreateOutboxEventWithPendingStatus() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        OutboxEvent result = outboxService.saveEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                testEvent
        );

        // Then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // =======================================
    //      FIND EVENT TESTS
    // =======================================

    @Test
    @DisplayName("Should find event by ID")
    void shouldFindEventById() {
        // Given
        when(outboxRepository.findById("evt_123")).thenReturn(Optional.of(testOutboxEvent));

        // When
        OutboxEvent result = outboxService.findById("evt_123");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAggregateId()).isEqualTo("pay_456");
        verify(outboxRepository).findById("evt_123");
    }

    @Test
    @DisplayName("Should return null when event not found")
    void shouldReturnNullWhenEventNotFound() {
        // Given
        when(outboxRepository.findById("non_existent")).thenReturn(Optional.empty());

        // When
        OutboxEvent result = outboxService.findById("non_existent");

        // Then
        assertThat(result).isNull();
        verify(outboxRepository).findById("non_existent");
    }

    // =======================================
    //      MARK AS PUBLISHED TESTS
    // =======================================

    @Test
    @DisplayName("Should mark event as published")
    void shouldMarkEventAsPublished() {
        // Given
        OutboxEvent event = new OutboxEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                "{}"
        );
        when(outboxRepository.findById("evt_123")).thenReturn(Optional.of(event));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(event);

        // When
        outboxService.markAsPublished("evt_123");

        // Then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(saved.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should not fail when marking non-existent event as published")
    void shouldNotFailWhenMarkingNonExistentEventAsPublished() {
        // Given
        when(outboxRepository.findById("non_existent")).thenReturn(Optional.empty());

        // When
        outboxService.markAsPublished("non_existent");

        // Then
        verify(outboxRepository, never()).save(any());
    }

    // =======================================
    //      MARK AS FAILED TESTS
    // =======================================

    @Test
    @DisplayName("Should mark event as failed with error message")
    void shouldMarkEventAsFailedWithErrorMessage() {
        // Given
        OutboxEvent event = new OutboxEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                "{}"
        );
        when(outboxRepository.findById("evt_123")).thenReturn(Optional.of(event));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(event);

        // When
        outboxService.markAsFailed("evt_123", "Kafka connection timeout");

        // Then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo("Kafka connection timeout");
    }

    @Test
    @DisplayName("Should not fail when marking non-existent event as failed")
    void shouldNotFailWhenMarkingNonExistentEventAsFailed() {
        // Given
        when(outboxRepository.findById("non_existent")).thenReturn(Optional.empty());

        // When
        outboxService.markAsFailed("non_existent", "Error message");

        // Then
        verify(outboxRepository, never()).save(any());
    }

    // =======================================
    //      RECORD ERROR TESTS
    // =======================================

    @Test
    @DisplayName("Should record error and increment retry count")
    void shouldRecordErrorAndIncrementRetryCount() {
        // Given
        OutboxEvent event = new OutboxEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                "{}"
        );
        when(outboxRepository.findById("evt_123")).thenReturn(Optional.of(event));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(event);

        // When
        outboxService.recordError("evt_123", "Temporary network error");

        // Then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getErrorMessage()).isEqualTo("Temporary network error");
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("Should increment retry count on multiple errors")
    void shouldIncrementRetryCountOnMultipleErrors() {
        // Given
        OutboxEvent event = new OutboxEvent(
                "PAYMENT",
                "pay_456",
                "PAYMENT_APPROVED",
                "payment.approved.v1",
                "user_789",
                "{}"
        );
        event.setRetryCount(2);

        when(outboxRepository.findById("evt_123")).thenReturn(Optional.of(event));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(event);

        // When
        outboxService.recordError("evt_123", "Another error");

        // Then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getRetryCount()).isEqualTo(3);
    }

    // =======================================
    //      COUNT BY STATUS TESTS
    // =======================================

    @Test
    @DisplayName("Should count events by status")
    void shouldCountEventsByStatus() {
        // Given
        when(outboxRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(5L);
        when(outboxRepository.countByStatus(OutboxEventStatus.PUBLISHED)).thenReturn(10L);
        when(outboxRepository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(2L);

        // When/Then
        assertThat(outboxService.countByStatus(OutboxEventStatus.PENDING)).isEqualTo(5L);
        assertThat(outboxService.countByStatus(OutboxEventStatus.PUBLISHED)).isEqualTo(10L);
        assertThat(outboxService.countByStatus(OutboxEventStatus.FAILED)).isEqualTo(2L);

        verify(outboxRepository, times(3)).countByStatus(any());
    }

    @Test
    @DisplayName("Should return zero when no events exist for status")
    void shouldReturnZeroWhenNoEventsExistForStatus() {
        // Given
        when(outboxRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(0L);

        // When
        long count = outboxService.countByStatus(OutboxEventStatus.PENDING);

        // Then
        assertThat(count).isZero();
    }
}
