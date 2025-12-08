package com.mvbr.store.infrastructure.adapter.in.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentApprovedRequestDto - Validation Tests")
class PaymentApprovedRequestDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // =======================================
    //      VALID DTO TESTS
    // =======================================

    @Test
    @DisplayName("Should validate successfully with valid data")
    void shouldValidateSuccessfullyWithValidData() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_123",
            "user_456",
            new BigDecimal("100.50"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty(), "Should have no validation errors");
    }

    // =======================================
    //      PAYMENT ID VALIDATION TESTS
    // =======================================

    @Test
    @DisplayName("Should fail validation when paymentId is null")
    void shouldFailValidationWhenPaymentIdIsNull() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            null,
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Payment ID cannot be null")));
    }

    @Test
    @DisplayName("Should fail validation when paymentId is blank")
    void shouldFailValidationWhenPaymentIdIsBlank() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "   ",
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v ->
            v.getMessage().contains("Payment ID cannot be blank") ||
            v.getMessage().contains("Payment ID must contain only alphanumeric")
        ));
    }

    @Test
    @DisplayName("Should fail validation when paymentId is empty")
    void shouldFailValidationWhenPaymentIdIsEmpty() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "",
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Should fail validation when paymentId exceeds 100 characters")
    void shouldFailValidationWhenPaymentIdExceeds100Characters() {
        // Arrange
        String longPaymentId = "a".repeat(101);
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            longPaymentId,
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("between 1 and 100 characters")));
    }

    @Test
    @DisplayName("Should fail validation when paymentId contains invalid characters")
    void shouldFailValidationWhenPaymentIdContainsInvalidCharacters() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay@123!",
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("alphanumeric characters")));
    }

    @Test
    @DisplayName("Should accept valid paymentId with hyphens and underscores")
    void shouldAcceptValidPaymentIdWithHyphensAndUnderscores() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_123-456",
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    // =======================================
    //      USER ID VALIDATION TESTS
    // =======================================

    @Test
    @DisplayName("Should fail validation when userId is null")
    void shouldFailValidationWhenUserIdIsNull() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            null,
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("User ID cannot be null")));
    }

    @Test
    @DisplayName("Should fail validation when userId is blank")
    void shouldFailValidationWhenUserIdIsBlank() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "   ",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Should fail validation when userId exceeds 100 characters")
    void shouldFailValidationWhenUserIdExceeds100Characters() {
        // Arrange
        String longUserId = "u".repeat(101);
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            longUserId,
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Should fail validation when userId contains invalid characters")
    void shouldFailValidationWhenUserIdContainsInvalidCharacters() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user@123",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    // =======================================
    //      AMOUNT VALIDATION TESTS
    // =======================================

    @Test
    @DisplayName("Should fail validation when amount is null")
    void shouldFailValidationWhenAmountIsNull() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            null,
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Amount cannot be null")));
    }

    @Test
    @DisplayName("Should fail validation when amount is zero")
    void shouldFailValidationWhenAmountIsZero() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            BigDecimal.ZERO,
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v ->
            v.getMessage().contains("positive") || v.getMessage().contains("at least 0.01")
        ));
    }

    @Test
    @DisplayName("Should fail validation when amount is negative")
    void shouldFailValidationWhenAmountIsNegative() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("-50.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("positive")));
    }

    @Test
    @DisplayName("Should fail validation when amount is below minimum (0.01)")
    void shouldFailValidationWhenAmountIsBelowMinimum() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("0.001"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Should fail validation when amount exceeds maximum")
    void shouldFailValidationWhenAmountExceedsMaximum() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("1000000000.00"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("999,999,999.99")));
    }

    @Test
    @DisplayName("Should fail validation when amount has more than 2 decimal places")
    void shouldFailValidationWhenAmountHasMoreThan2DecimalPlaces() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.123"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("exactly 2 after")));
    }

    @Test
    @DisplayName("Should accept minimum valid amount (0.01)")
    void shouldAcceptMinimumValidAmount() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("0.01"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Should accept maximum valid amount")
    void shouldAcceptMaximumValidAmount() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("999999999.99"),
            "USD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertTrue(violations.isEmpty());
    }

    // =======================================
    //      CURRENCY VALIDATION TESTS
    // =======================================

    @Test
    @DisplayName("Should fail validation when currency is null")
    void shouldFailValidationWhenCurrencyIsNull() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            null
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Currency cannot be null")));
    }

    @Test
    @DisplayName("Should fail validation when currency is blank")
    void shouldFailValidationWhenCurrencyIsBlank() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            "   "
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Should fail validation when currency is not 3 characters")
    void shouldFailValidationWhenCurrencyIsNot3Characters() {
        // Arrange - 2 characters
        PaymentApprovedRequestDto dto1 = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            "US"
        );

        // Arrange - 4 characters
        PaymentApprovedRequestDto dto2 = new PaymentApprovedRequestDto(
            "pay_2",
            "user_2",
            new BigDecimal("100.00"),
            "USDD"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations1 = validator.validate(dto1);
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations2 = validator.validate(dto2);

        // Assert
        assertFalse(violations1.isEmpty());
        assertFalse(violations2.isEmpty());
    }

    @Test
    @DisplayName("Should fail validation when currency is not uppercase")
    void shouldFailValidationWhenCurrencyIsNotUppercase() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            "usd"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("uppercase letters")));
    }

    @Test
    @DisplayName("Should fail validation when currency contains numbers")
    void shouldFailValidationWhenCurrencyContainsNumbers() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            "US1"
        );

        // Act
        Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);

        // Assert
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Should accept valid ISO 4217 currency codes")
    void shouldAcceptValidISO4217CurrencyCodes() {
        // Test multiple valid currencies
        String[] validCurrencies = {"USD", "EUR", "BRL", "GBP", "JPY", "CNY"};

        for (String currency : validCurrencies) {
            PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
                "pay_1",
                "user_1",
                new BigDecimal("100.00"),
                currency
            );

            Set<ConstraintViolation<PaymentApprovedRequestDto>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty(), "Should accept currency: " + currency);
        }
    }
}
