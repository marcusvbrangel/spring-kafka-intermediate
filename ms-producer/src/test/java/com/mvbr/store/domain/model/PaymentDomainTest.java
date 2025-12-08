package com.mvbr.store.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentDomain - Unit Tests")
class PaymentDomainTest {

    // =======================================
    //      CONSTRUCTION TESTS - VALID
    // =======================================

    @Test
    @DisplayName("Should create payment with valid data")
    void shouldCreatePaymentWithValidData() {
        // Arrange
        String paymentId = "pay_123";
        String userId = "user_456";
        BigDecimal amount = new BigDecimal("100.50");
        String currency = "usd";

        // Act
        PaymentDomain payment = new PaymentDomain(paymentId, userId, amount, currency);

        // Assert
        assertNotNull(payment);
        assertEquals(paymentId, payment.getPaymentId());
        assertEquals(userId, payment.getUserId());
        assertEquals(amount, payment.getAmount());
        assertEquals("USD", payment.getCurrency()); // Should be uppercase
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertTrue(payment.getCreatedAt() > 0);
    }

    @Test
    @DisplayName("Should convert currency to uppercase")
    void shouldConvertCurrencyToUppercase() {
        // Arrange & Act
        PaymentDomain payment = new PaymentDomain("pay_1", "user_1",
            new BigDecimal("50.00"), "brl");

        // Assert
        assertEquals("BRL", payment.getCurrency());
    }

    @Test
    @DisplayName("Should initialize with PENDING status")
    void shouldInitializeWithPendingStatus() {
        // Arrange & Act
        PaymentDomain payment = new PaymentDomain("pay_1", "user_1",
            new BigDecimal("50.00"), "USD");

        // Assert
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    @DisplayName("Should restore payment from database with all fields")
    void shouldRestorePaymentFromDatabase() {
        // Arrange
        String paymentId = "pay_789";
        String userId = "user_123";
        BigDecimal amount = new BigDecimal("250.00");
        String currency = "EUR";
        PaymentStatus status = PaymentStatus.APPROVED;
        long createdAt = System.currentTimeMillis() - 10000;

        // Act
        PaymentDomain payment = new PaymentDomain(paymentId, userId, amount,
            currency, status, createdAt);

        // Assert
        assertEquals(paymentId, payment.getPaymentId());
        assertEquals(userId, payment.getUserId());
        assertEquals(amount, payment.getAmount());
        assertEquals(currency, payment.getCurrency());
        assertEquals(status, payment.getStatus());
        assertEquals(createdAt, payment.getCreatedAt());
    }

    // =======================================
    //      VALIDATION TESTS - PAYMENT ID
    // =======================================

    @Test
    @DisplayName("Should throw exception when paymentId is null")
    void shouldThrowExceptionWhenPaymentIdIsNull() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain(null, "user_1", new BigDecimal("100.00"), "USD")
        );

        assertEquals("paymentId cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when paymentId is empty")
    void shouldThrowExceptionWhenPaymentIdIsEmpty() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("", "user_1", new BigDecimal("100.00"), "USD")
        );

        assertEquals("paymentId cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when paymentId is blank")
    void shouldThrowExceptionWhenPaymentIdIsBlank() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("   ", "user_1", new BigDecimal("100.00"), "USD")
        );

        assertEquals("paymentId cannot be null or empty", exception.getMessage());
    }

    // =======================================
    //      VALIDATION TESTS - USER ID
    // =======================================

    @Test
    @DisplayName("Should throw exception when userId is null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", null, new BigDecimal("100.00"), "USD")
        );

        assertEquals("userId cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when userId is empty")
    void shouldThrowExceptionWhenUserIdIsEmpty() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", "", new BigDecimal("100.00"), "USD")
        );

        assertEquals("userId cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when userId is blank")
    void shouldThrowExceptionWhenUserIdIsBlank() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", "   ", new BigDecimal("100.00"), "USD")
        );

        assertEquals("userId cannot be null or empty", exception.getMessage());
    }

    // =======================================
    //      VALIDATION TESTS - AMOUNT
    // =======================================

    @Test
    @DisplayName("Should throw exception when amount is null")
    void shouldThrowExceptionWhenAmountIsNull() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", "user_1", null, "USD")
        );

        assertEquals("amount must be greater than zero", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when amount is zero")
    void shouldThrowExceptionWhenAmountIsZero() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", "user_1", BigDecimal.ZERO, "USD")
        );

        assertEquals("amount must be greater than zero", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when amount is negative")
    void shouldThrowExceptionWhenAmountIsNegative() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", "user_1", new BigDecimal("-50.00"), "USD")
        );

        assertEquals("amount must be greater than zero", exception.getMessage());
    }

    // =======================================
    //      VALIDATION TESTS - CURRENCY
    // =======================================

    @Test
    @DisplayName("Should throw exception when currency is null")
    void shouldThrowExceptionWhenCurrencyIsNull() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", "user_1", new BigDecimal("100.00"), null)
        );

        assertEquals("currency cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when currency is empty")
    void shouldThrowExceptionWhenCurrencyIsEmpty() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", "user_1", new BigDecimal("100.00"), "")
        );

        assertEquals("currency cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when currency is blank")
    void shouldThrowExceptionWhenCurrencyIsBlank() {
        // Arrange & Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new PaymentDomain("pay_1", "user_1", new BigDecimal("100.00"), "   ")
        );

        assertEquals("currency cannot be null or empty", exception.getMessage());
    }

    // =======================================
    //      BUSINESS LOGIC - MARK APPROVED
    // =======================================

    @Test
    @DisplayName("Should mark payment as APPROVED when status is PENDING")
    void shouldMarkPaymentAsApprovedWhenStatusIsPending() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_1", "user_1",
            new BigDecimal("100.00"), "USD");

        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        // Act
        payment.markApproved();

        // Assert
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when trying to approve a CANCELED payment")
    void shouldThrowExceptionWhenTryingToApproveCanceledPayment() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_1", "user_1",
            new BigDecimal("100.00"), "USD", PaymentStatus.CANCELED,
            System.currentTimeMillis());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            payment.markApproved()
        );

        assertEquals("Cannot approve a canceled payment", exception.getMessage());
        assertEquals(PaymentStatus.CANCELED, payment.getStatus()); // Status should remain CANCELED
    }

    @Test
    @DisplayName("Should mark already APPROVED payment as APPROVED (idempotent)")
    void shouldMarkAlreadyApprovedPaymentAsApproved() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_1", "user_1",
            new BigDecimal("100.00"), "USD", PaymentStatus.APPROVED,
            System.currentTimeMillis());

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());

        // Act
        payment.markApproved(); // Should not throw exception

        // Assert
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
    }

    // =======================================
    //      BUSINESS LOGIC - CANCEL
    // =======================================

    @Test
    @DisplayName("Should cancel payment when status is PENDING")
    void shouldCancelPaymentWhenStatusIsPending() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_1", "user_1",
            new BigDecimal("100.00"), "USD");

        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        // Act
        payment.cancel();

        // Assert
        assertEquals(PaymentStatus.CANCELED, payment.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when trying to cancel an APPROVED payment")
    void shouldThrowExceptionWhenTryingToCancelApprovedPayment() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_1", "user_1",
            new BigDecimal("100.00"), "USD", PaymentStatus.APPROVED,
            System.currentTimeMillis());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            payment.cancel()
        );

        assertEquals("Cannot cancel an approved payment", exception.getMessage());
        assertEquals(PaymentStatus.APPROVED, payment.getStatus()); // Status should remain APPROVED
    }

    @Test
    @DisplayName("Should cancel already CANCELED payment (idempotent)")
    void shouldCancelAlreadyCanceledPayment() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_1", "user_1",
            new BigDecimal("100.00"), "USD", PaymentStatus.CANCELED,
            System.currentTimeMillis());

        assertEquals(PaymentStatus.CANCELED, payment.getStatus());

        // Act
        payment.cancel(); // Should not throw exception

        // Assert
        assertEquals(PaymentStatus.CANCELED, payment.getStatus());
    }

    // =======================================
    //      EQUALS & HASHCODE TESTS
    // =======================================

    @Test
    @DisplayName("Should be equal when paymentId is the same")
    void shouldBeEqualWhenPaymentIdIsTheSame() {
        // Arrange
        PaymentDomain payment1 = new PaymentDomain("pay_123", "user_1",
            new BigDecimal("100.00"), "USD");

        PaymentDomain payment2 = new PaymentDomain("pay_123", "user_2",
            new BigDecimal("200.00"), "EUR");

        // Assert
        assertEquals(payment1, payment2);
        assertEquals(payment1.hashCode(), payment2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when paymentId is different")
    void shouldNotBeEqualWhenPaymentIdIsDifferent() {
        // Arrange
        PaymentDomain payment1 = new PaymentDomain("pay_123", "user_1",
            new BigDecimal("100.00"), "USD");

        PaymentDomain payment2 = new PaymentDomain("pay_456", "user_1",
            new BigDecimal("100.00"), "USD");

        // Assert
        assertNotEquals(payment1, payment2);
        assertNotEquals(payment1.hashCode(), payment2.hashCode());
    }

    @Test
    @DisplayName("Should be equal to itself")
    void shouldBeEqualToItself() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_123", "user_1",
            new BigDecimal("100.00"), "USD");

        // Assert
        assertEquals(payment, payment);
    }

    @Test
    @DisplayName("Should not be equal to null")
    void shouldNotBeEqualToNull() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_123", "user_1",
            new BigDecimal("100.00"), "USD");

        // Assert
        assertNotEquals(payment, null);
    }

    @Test
    @DisplayName("Should not be equal to different class")
    void shouldNotBeEqualToDifferentClass() {
        // Arrange
        PaymentDomain payment = new PaymentDomain("pay_123", "user_1",
            new BigDecimal("100.00"), "USD");

        String differentObject = "pay_123";

        // Assert
        assertNotEquals(payment, differentObject);
    }
}
