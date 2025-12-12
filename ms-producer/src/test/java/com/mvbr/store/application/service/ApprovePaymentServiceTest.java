package com.mvbr.store.application.service;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.out.PaymentRepositoryPort;
import com.mvbr.store.domain.model.PaymentDomain;
import com.mvbr.store.domain.model.PaymentStatus;
import com.mvbr.store.infrastructure.adapter.out.outbox.OutboxEvent;
import com.mvbr.store.infrastructure.adapter.out.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for ApprovePaymentService.
 *
 * Tests the application service layer:
 * - Business orchestration
 * - Domain model creation and validation
 * - Repository interaction
 * - Outbox Pattern integration
 * - Transaction boundaries
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovePaymentService - Application Layer Tests")
class ApprovePaymentServiceTest {

    @Mock
    private PaymentRepositoryPort paymentRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private ApprovePaymentService service;

    private ApprovePaymentCommand validCommand;
    private PaymentDomain savedPayment;

    @BeforeEach
    void setUp() {
        // Set the topic value
        ReflectionTestUtils.setField(service, "paymentApprovedTopic", "payment.approved.v1");

        // Create valid command
        validCommand = new ApprovePaymentCommand(
                "pay_123",
                "user_456",
                new BigDecimal("100.00"),
                "USD"
        );

        // Create saved payment (as if returned from repository)
        savedPayment = PaymentDomain.of(
                "pay_123",
                "user_456",
                new BigDecimal("100.00"),
                "USD",
                PaymentStatus.APPROVED,
                System.currentTimeMillis()
        );
    }

    // =======================================
    //      SUCCESSFUL PAYMENT APPROVAL
    // =======================================

    @Test
    @DisplayName("Should approve payment successfully with valid command")
    void shouldApprovePaymentSuccessfullyWithValidCommand() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        PaymentResponse response = service.approvePayment(validCommand);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo("pay_123");
        assertThat(response.userId()).isEqualTo("user_456");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.createdAt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should create domain model from command")
    void shouldCreateDomainModelFromCommand() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(validCommand);

        // Then
        ArgumentCaptor<PaymentDomain> captor = ArgumentCaptor.forClass(PaymentDomain.class);
        verify(paymentRepository).save(captor.capture());

        PaymentDomain domain = captor.getValue();
        assertThat(domain.getPaymentId()).isEqualTo("pay_123");
        assertThat(domain.getUserId()).isEqualTo("user_456");
        assertThat(domain.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(domain.getCurrency()).isEqualTo("USD");
        assertThat(domain.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("Should mark payment as approved before saving")
    void shouldMarkPaymentAsApprovedBeforeSaving() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(validCommand);

        // Then
        ArgumentCaptor<PaymentDomain> captor = ArgumentCaptor.forClass(PaymentDomain.class);
        verify(paymentRepository).save(captor.capture());

        PaymentDomain domain = captor.getValue();
        assertThat(domain.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("Should persist payment to repository")
    void shouldPersistPaymentToRepository() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(validCommand);

        // Then
        verify(paymentRepository, times(1)).save(any(PaymentDomain.class));
    }

    // =======================================
    //      OUTBOX PATTERN INTEGRATION
    // =======================================

    @Test
    @DisplayName("Should save event to outbox after persisting payment")
    void shouldSaveEventToOutboxAfterPersistingPayment() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(validCommand);

        // Then
        verify(outboxService, times(1)).saveEvent(
                eq("PAYMENT"),
                eq("pay_123"),
                eq("PAYMENT_APPROVED"),
                eq("payment.approved.v1"),
                eq("user_456"),
                any()
        );
    }

    @Test
    @DisplayName("Should use userId as partition key in outbox event")
    void shouldUseUserIdAsPartitionKeyInOutboxEvent() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(validCommand);

        // Then
        ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveEvent(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                partitionKeyCaptor.capture(),
                any()
        );

        assertThat(partitionKeyCaptor.getValue()).isEqualTo("user_456");
    }

    @Test
    @DisplayName("Should create event with correct aggregate type and ID")
    void shouldCreateEventWithCorrectAggregateTypeAndId() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(validCommand);

        // Then
        verify(outboxService).saveEvent(
                eq("PAYMENT"),           // aggregateType
                eq("pay_123"),           // aggregateId
                anyString(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    @DisplayName("Should use configured topic for outbox event")
    void shouldUseConfiguredTopicForOutboxEvent() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(validCommand);

        // Then
        verify(outboxService).saveEvent(
                anyString(),
                anyString(),
                anyString(),
                eq("payment.approved.v1"),  // topic
                anyString(),
                any()
        );
    }

    // =======================================
    //      VALIDATION TESTS
    // =======================================

    @Test
    @DisplayName("Should throw exception when paymentId is null")
    void shouldThrowExceptionWhenPaymentIdIsNull() {
        // Given
        ApprovePaymentCommand invalidCommand = new ApprovePaymentCommand(
                null,
                "user_456",
                new BigDecimal("100.00"),
                "USD"
        );

        // When/Then
        assertThatThrownBy(() -> service.approvePayment(invalidCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentId cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when paymentId is blank")
    void shouldThrowExceptionWhenPaymentIdIsBlank() {
        // Given
        ApprovePaymentCommand invalidCommand = new ApprovePaymentCommand(
                "   ",
                "user_456",
                new BigDecimal("100.00"),
                "USD"
        );

        // When/Then
        assertThatThrownBy(() -> service.approvePayment(invalidCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentId cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when userId is null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        // Given
        ApprovePaymentCommand invalidCommand = new ApprovePaymentCommand(
                "pay_123",
                null,
                new BigDecimal("100.00"),
                "USD"
        );

        // When/Then
        assertThatThrownBy(() -> service.approvePayment(invalidCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when amount is null")
    void shouldThrowExceptionWhenAmountIsNull() {
        // Given
        ApprovePaymentCommand invalidCommand = new ApprovePaymentCommand(
                "pay_123",
                "user_456",
                null,
                "USD"
        );

        // When/Then
        assertThatThrownBy(() -> service.approvePayment(invalidCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be greater than zero");
    }

    @Test
    @DisplayName("Should throw exception when amount is zero")
    void shouldThrowExceptionWhenAmountIsZero() {
        // Given
        ApprovePaymentCommand invalidCommand = new ApprovePaymentCommand(
                "pay_123",
                "user_456",
                BigDecimal.ZERO,
                "USD"
        );

        // When/Then
        assertThatThrownBy(() -> service.approvePayment(invalidCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be greater than zero");
    }

    @Test
    @DisplayName("Should throw exception when amount is negative")
    void shouldThrowExceptionWhenAmountIsNegative() {
        // Given
        ApprovePaymentCommand invalidCommand = new ApprovePaymentCommand(
                "pay_123",
                "user_456",
                new BigDecimal("-10.00"),
                "USD"
        );

        // When/Then
        assertThatThrownBy(() -> service.approvePayment(invalidCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be greater than zero");
    }

    @Test
    @DisplayName("Should throw exception when currency is null")
    void shouldThrowExceptionWhenCurrencyIsNull() {
        // Given
        ApprovePaymentCommand invalidCommand = new ApprovePaymentCommand(
                "pay_123",
                "user_456",
                new BigDecimal("100.00"),
                null
        );

        // When/Then
        assertThatThrownBy(() -> service.approvePayment(invalidCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency cannot be null");
    }

    // =======================================
    //      BUSINESS LOGIC TESTS
    // =======================================

    @Test
    @DisplayName("Should convert currency to uppercase")
    void shouldConvertCurrencyToUppercase() {
        // Given
        ApprovePaymentCommand commandWithLowercase = new ApprovePaymentCommand(
                "pay_123",
                "user_456",
                new BigDecimal("100.00"),
                "usd"
        );

        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(commandWithLowercase);

        // Then
        ArgumentCaptor<PaymentDomain> captor = ArgumentCaptor.forClass(PaymentDomain.class);
        verify(paymentRepository).save(captor.capture());

        assertThat(captor.getValue().getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should handle different currencies")
    void shouldHandleDifferentCurrencies() {
        // Given
        String[] currencies = {"USD", "EUR", "BRL", "GBP", "JPY"};

        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When/Then
        for (String currency : currencies) {
            // Create a specific saved payment for each currency
            PaymentDomain currencyPayment = PaymentDomain.of(
                    "pay_" + currency,
                    "user_456",
                    new BigDecimal("100.00"),
                    currency,
                    PaymentStatus.APPROVED,
                    System.currentTimeMillis()
            );

            when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(currencyPayment);

            ApprovePaymentCommand command = new ApprovePaymentCommand(
                    "pay_" + currency,
                    "user_456",
                    new BigDecimal("100.00"),
                    currency
            );

            PaymentResponse response = service.approvePayment(command);
            assertThat(response.currency()).isEqualTo(currency);
        }
    }

    @Test
    @DisplayName("Should handle large amounts")
    void shouldHandleLargeAmounts() {
        // Given
        ApprovePaymentCommand largeAmountCommand = new ApprovePaymentCommand(
                "pay_large",
                "user_456",
                new BigDecimal("999999999.99"),
                "USD"
        );

        PaymentDomain largePayment = PaymentDomain.of(
                "pay_large",
                "user_456",
                new BigDecimal("999999999.99"),
                "USD",
                PaymentStatus.APPROVED,
                System.currentTimeMillis()
        );

        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(largePayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        PaymentResponse response = service.approvePayment(largeAmountCommand);

        // Then
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("999999999.99"));
    }

    @Test
    @DisplayName("Should handle minimum valid amount")
    void shouldHandleMinimumValidAmount() {
        // Given
        ApprovePaymentCommand minAmountCommand = new ApprovePaymentCommand(
                "pay_min",
                "user_456",
                new BigDecimal("0.01"),
                "USD"
        );

        PaymentDomain minPayment = PaymentDomain.of(
                "pay_min",
                "user_456",
                new BigDecimal("0.01"),
                "USD",
                PaymentStatus.APPROVED,
                System.currentTimeMillis()
        );

        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(minPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        PaymentResponse response = service.approvePayment(minAmountCommand);

        // Then
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    // =======================================
    //      TRANSACTIONAL BEHAVIOR
    // =======================================

    @Test
    @DisplayName("Should save both payment and outbox event in same transaction")
    void shouldSaveBothPaymentAndOutboxEventInSameTransaction() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class))).thenReturn(savedPayment);
        when(outboxService.saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(OutboxEvent.class));

        // When
        service.approvePayment(validCommand);

        // Then
        verify(paymentRepository, times(1)).save(any(PaymentDomain.class));
        verify(outboxService, times(1)).saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should not save outbox event if repository save fails")
    void shouldNotSaveOutboxEventIfRepositorySaveFails() {
        // Given
        when(paymentRepository.save(any(PaymentDomain.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When/Then
        assertThatThrownBy(() -> service.approvePayment(validCommand))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection failed");

        verify(outboxService, never()).saveEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }
}
