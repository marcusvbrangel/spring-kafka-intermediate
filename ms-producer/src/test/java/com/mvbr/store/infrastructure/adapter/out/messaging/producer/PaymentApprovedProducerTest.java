package com.mvbr.store.infrastructure.adapter.out.messaging.producer;

import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for PaymentApprovedProducer.
 *
 * Tests Kafka event production:
 * - Message creation with headers
 * - Partition key (userId for ordering)
 * - Success/failure callbacks
 * - Logging behavior
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentApprovedProducer - Unit Tests")
class PaymentApprovedProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PaymentApprovedProducer producer;

    private PaymentApprovedEvent testEvent;

    @BeforeEach
    void setUp() {
        producer = new PaymentApprovedProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "paymentApprovedTopic", "payment.approved.v1");

        testEvent = new PaymentApprovedEvent(
                "evt_123",
                "pay_456",
                "user_789",
                new BigDecimal("100.00"),
                "USD",
                "APPROVED",
                Instant.now().toEpochMilli()
        );
    }

    // =======================================
    //      BASIC SEND TESTS
    // =======================================

    @Test
    @DisplayName("Should send event to correct topic")
    void shouldSendEventToCorrectTopic() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("payment.approved.v1");
    }

    @Test
    @DisplayName("Should use userId as partition key for ordering")
    void shouldUseUserIdAsPartitionKeyForOrdering() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.key()).isEqualTo("user_789");
    }

    @Test
    @DisplayName("Should include event as payload")
    void shouldIncludeEventAsPayload() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.value()).isEqualTo(testEvent);
    }

    // =======================================
    //      HEADERS TESTS
    // =======================================

    @Test
    @DisplayName("Should add event-type header")
    void shouldAddEventTypeHeader() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.headers()).isNotNull();

        byte[] eventTypeHeader = record.headers().lastHeader("event-type").value();
        String eventType = new String(eventTypeHeader, StandardCharsets.UTF_8);
        assertThat(eventType).isEqualTo("PAYMENT_APPROVED");
    }

    @Test
    @DisplayName("Should add service header")
    void shouldAddServiceHeader() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();

        byte[] serviceHeader = record.headers().lastHeader("service").value();
        String service = new String(serviceHeader, StandardCharsets.UTF_8);
        assertThat(service).isEqualTo("payment-service");
    }

    @Test
    @DisplayName("Should add schema-version header")
    void shouldAddSchemaVersionHeader() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();

        byte[] versionHeader = record.headers().lastHeader("schema-version").value();
        String version = new String(versionHeader, StandardCharsets.UTF_8);
        assertThat(version).isEqualTo("v1");
    }

    @Test
    @DisplayName("Should have all three required headers")
    void shouldHaveAllThreeRequiredHeaders() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.headers().toArray()).hasSize(3);
        assertThat(record.headers().lastHeader("event-type")).isNotNull();
        assertThat(record.headers().lastHeader("service")).isNotNull();
        assertThat(record.headers().lastHeader("schema-version")).isNotNull();
    }

    // =======================================
    //      SUCCESS CALLBACK TESTS
    // =======================================

    @Test
    @DisplayName("Should handle successful send")
    void shouldHandleSuccessfulSend() {
        // Given
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("payment.approved.v1", 0),
                0L,
                0L,
                System.currentTimeMillis(),
                0L,
                0,
                0
        );

        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        verify(kafkaTemplate).send(any(ProducerRecord.class));
        // Success callback should be invoked (logs would show success message)
    }

    // =======================================
    //      FAILURE CALLBACK TESTS
    // =======================================

    @Test
    @DisplayName("Should handle send failure")
    void shouldHandleSendFailure() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka broker unavailable"));

        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        verify(kafkaTemplate).send(any(ProducerRecord.class));
        // Error callback should be invoked (logs would show error message)
    }

    @Test
    @DisplayName("Should handle timeout exception")
    void shouldHandleTimeoutException() {
        // Given
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("Request timeout"));

        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(testEvent);

        // Then
        verify(kafkaTemplate).send(any(ProducerRecord.class));
        // Error callback should handle timeout gracefully
    }

    // =======================================
    //      ORDERING GUARANTEE TESTS
    // =======================================

    @Test
    @DisplayName("Should guarantee ordering for same userId")
    void shouldGuaranteeOrderingForSameUserId() {
        // Given
        PaymentApprovedEvent event1 = new PaymentApprovedEvent(
                "evt_1", "pay_1", "user_123",
                new BigDecimal("50.00"), "USD", "APPROVED",
                Instant.now().toEpochMilli()
        );

        PaymentApprovedEvent event2 = new PaymentApprovedEvent(
                "evt_2", "pay_2", "user_123",
                new BigDecimal("75.00"), "USD", "APPROVED",
                Instant.now().toEpochMilli()
        );

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(event1);
        producer.producePaymentApproved(event2);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());

        List<ProducerRecord<String, Object>> records = captor.getAllValues();
        // Both events should use same partition key (userId)
        assertThat(records.get(0).key()).isEqualTo("user_123");
        assertThat(records.get(1).key()).isEqualTo("user_123");
    }

    @Test
    @DisplayName("Should allow parallel processing for different userIds")
    void shouldAllowParallelProcessingForDifferentUserIds() {
        // Given
        PaymentApprovedEvent event1 = new PaymentApprovedEvent(
                "evt_1", "pay_1", "user_111",
                new BigDecimal("50.00"), "USD", "APPROVED",
                Instant.now().toEpochMilli()
        );

        PaymentApprovedEvent event2 = new PaymentApprovedEvent(
                "evt_2", "pay_2", "user_222",
                new BigDecimal("75.00"), "USD", "APPROVED",
                Instant.now().toEpochMilli()
        );

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(event1);
        producer.producePaymentApproved(event2);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(captor.capture());

        List<ProducerRecord<String, Object>> records = captor.getAllValues();
        // Different partition keys allow parallel processing
        assertThat(records.get(0).key()).isEqualTo("user_111");
        assertThat(records.get(1).key()).isEqualTo("user_222");
    }

    // =======================================
    //      EDGE CASES
    // =======================================

    @Test
    @DisplayName("Should handle event with large amount")
    void shouldHandleEventWithLargeAmount() {
        // Given
        PaymentApprovedEvent largeAmountEvent = new PaymentApprovedEvent(
                "evt_large",
                "pay_large",
                "user_789",
                new BigDecimal("999999999.99"),
                "USD",
                "APPROVED",
                Instant.now().toEpochMilli()
        );

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        producer.producePaymentApproved(largeAmountEvent);

        // Then
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> record = captor.getValue();
        PaymentApprovedEvent sentEvent = (PaymentApprovedEvent) record.value();
        assertThat(sentEvent.amount()).isEqualByComparingTo(new BigDecimal("999999999.99"));
    }

    @Test
    @DisplayName("Should handle event with different currencies")
    void shouldHandleEventWithDifferentCurrencies() {
        // Given
        String[] currencies = {"USD", "EUR", "BRL", "GBP", "JPY"};

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        for (String currency : currencies) {
            PaymentApprovedEvent event = new PaymentApprovedEvent(
                    "evt_" + currency,
                    "pay_" + currency,
                    "user_789",
                    new BigDecimal("100.00"),
                    currency,
                    "APPROVED",
                    Instant.now().toEpochMilli()
            );
            producer.producePaymentApproved(event);
        }

        // Then
        verify(kafkaTemplate, times(5)).send(any(ProducerRecord.class));
    }
}
