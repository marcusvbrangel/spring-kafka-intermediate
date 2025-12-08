package com.mvbr.store.infrastructure.adapter.in.web.mapper;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.domain.model.PaymentStatus;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequestDto;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentWebMapper - Unit Tests")
class PaymentWebMapperTest {

    private PaymentWebMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentWebMapper();
    }

    // =======================================
    //      DTO → COMMAND MAPPING
    // =======================================

    @Test
    @DisplayName("Should map PaymentApprovedRequestDto to ApprovePaymentCommand")
    void shouldMapRequestDtoToCommand() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_123",
            "user_456",
            new BigDecimal("100.50"),
            "USD"
        );

        // Act
        ApprovePaymentCommand command = mapper.toApprovePaymentCommand(dto);

        // Assert
        assertNotNull(command);
        assertEquals(dto.paymentId(), command.paymentId());
        assertEquals(dto.userId(), command.userId());
        assertEquals(dto.amount(), command.amount());
        assertEquals(dto.currency(), command.currency());
    }

    @Test
    @DisplayName("Should preserve all field values when mapping DTO to Command")
    void shouldPreserveAllFieldsWhenMappingDtoToCommand() {
        // Arrange
        PaymentApprovedRequestDto dto = new PaymentApprovedRequestDto(
            "pay_999",
            "user_ABC",
            new BigDecimal("999.99"),
            "BRL"
        );

        // Act
        ApprovePaymentCommand command = mapper.toApprovePaymentCommand(dto);

        // Assert
        assertEquals("pay_999", command.paymentId());
        assertEquals("user_ABC", command.userId());
        assertEquals(new BigDecimal("999.99"), command.amount());
        assertEquals("BRL", command.currency());
    }

    // =======================================
    //      RESPONSE → DTO MAPPING
    // =======================================

    @Test
    @DisplayName("Should map PaymentResponse to PaymentResponseDto")
    void shouldMapResponseToDto() {
        // Arrange
        long createdAt = System.currentTimeMillis();
        PaymentResponse response = new PaymentResponse(
            "pay_123",
            "user_456",
            new BigDecimal("100.50"),
            "USD",
            PaymentStatus.APPROVED,
            createdAt
        );

        // Act
        PaymentResponseDto dto = mapper.toPaymentResponseDto(response);

        // Assert
        assertNotNull(dto);
        assertEquals(response.paymentId(), dto.paymentId());
        assertEquals(response.userId(), dto.userId());
        assertEquals(response.amount(), dto.amount());
        assertEquals(response.currency(), dto.currency());
        assertEquals("APPROVED", dto.status()); // Enum name as string
        assertEquals(response.createdAt(), dto.createdAt());
    }

    @Test
    @DisplayName("Should convert PaymentStatus enum to String in DTO")
    void shouldConvertPaymentStatusToString() {
        // Arrange
        PaymentResponse response = new PaymentResponse(
            "pay_1",
            "user_1",
            new BigDecimal("50.00"),
            "EUR",
            PaymentStatus.PENDING,
            System.currentTimeMillis()
        );

        // Act
        PaymentResponseDto dto = mapper.toPaymentResponseDto(response);

        // Assert
        assertEquals("PENDING", dto.status());
        assertTrue(dto.status() instanceof String);
    }

    @Test
    @DisplayName("Should handle different PaymentStatus values")
    void shouldHandleDifferentPaymentStatusValues() {
        // Test APPROVED
        PaymentResponse approvedResponse = new PaymentResponse(
            "pay_1", "user_1", new BigDecimal("100.00"), "USD",
            PaymentStatus.APPROVED, System.currentTimeMillis()
        );
        PaymentResponseDto approvedDto = mapper.toPaymentResponseDto(approvedResponse);
        assertEquals("APPROVED", approvedDto.status());

        // Test PENDING
        PaymentResponse pendingResponse = new PaymentResponse(
            "pay_2", "user_2", new BigDecimal("200.00"), "EUR",
            PaymentStatus.PENDING, System.currentTimeMillis()
        );
        PaymentResponseDto pendingDto = mapper.toPaymentResponseDto(pendingResponse);
        assertEquals("PENDING", pendingDto.status());

        // Test CANCELED
        PaymentResponse canceledResponse = new PaymentResponse(
            "pay_3", "user_3", new BigDecimal("300.00"), "BRL",
            PaymentStatus.CANCELED, System.currentTimeMillis()
        );
        PaymentResponseDto canceledDto = mapper.toPaymentResponseDto(canceledResponse);
        assertEquals("CANCELED", canceledDto.status());
    }

    @Test
    @DisplayName("Should preserve exact BigDecimal values during mapping")
    void shouldPreserveExactBigDecimalValues() {
        // Arrange
        BigDecimal preciseAmount = new BigDecimal("123.45");
        PaymentResponse response = new PaymentResponse(
            "pay_1", "user_1", preciseAmount, "USD",
            PaymentStatus.APPROVED, System.currentTimeMillis()
        );

        // Act
        PaymentResponseDto dto = mapper.toPaymentResponseDto(response);

        // Assert
        assertEquals(preciseAmount, dto.amount());
        assertEquals(0, preciseAmount.compareTo(dto.amount()));
    }
}
