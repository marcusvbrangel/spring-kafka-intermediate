package com.mvbr.store.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.command.PaymentResponse;
import com.mvbr.store.application.port.in.ApprovePaymentUseCase;
import com.mvbr.store.domain.exception.InvalidPaymentException;
import com.mvbr.store.domain.exception.PaymentNotFoundException;
import com.mvbr.store.domain.model.PaymentStatus;
import com.mvbr.store.infrastructure.adapter.in.web.dto.PaymentApprovedRequestDto;
import com.mvbr.store.infrastructure.adapter.in.web.mapper.PaymentWebMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentWebController.class)
@ActiveProfiles("test")
@DisplayName("PaymentWebController - Integration Tests")
class PaymentWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApprovePaymentUseCase approvePaymentUseCase;

    @MockitoBean
    private PaymentWebMapper mapper;

    // =======================================
    //      SUCCESS SCENARIOS
    // =======================================

    @Test
    @DisplayName("Should approve payment successfully with valid request")
    void shouldApprovePaymentSuccessfully() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_123",
            "user_456",
            new BigDecimal("100.50"),
            "USD"
        );

        ApprovePaymentCommand command = new ApprovePaymentCommand(
            "pay_123", "user_456", new BigDecimal("100.50"), "USD"
        );

        PaymentResponse response = new PaymentResponse(
            "pay_123",
            "user_456",
            new BigDecimal("100.50"),
            "USD",
            PaymentStatus.APPROVED,
            System.currentTimeMillis()
        );

        when(mapper.toApprovePaymentCommand(any())).thenReturn(command);
        when(approvePaymentUseCase.approvePayment(any())).thenReturn(response);
        when(mapper.toPaymentResponseDto(any())).thenCallRealMethod();

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.paymentId").value("pay_123"))
            .andExpect(jsonPath("$.userId").value("user_456"))
            .andExpect(jsonPath("$.amount").value(100.50))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Should accept payment with minimum valid amount")
    void shouldAcceptPaymentWithMinimumValidAmount() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("0.01"),
            "BRL"
        );

        when(mapper.toApprovePaymentCommand(any())).thenReturn(
            new ApprovePaymentCommand("pay_1", "user_1", new BigDecimal("0.01"), "BRL")
        );
        when(approvePaymentUseCase.approvePayment(any())).thenReturn(
            new PaymentResponse("pay_1", "user_1", new BigDecimal("0.01"),
                "BRL", PaymentStatus.APPROVED, System.currentTimeMillis())
        );
        when(mapper.toPaymentResponseDto(any())).thenCallRealMethod();

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(0.01));
    }

    // =======================================
    //      VALIDATION ERRORS (HTTP 400)
    // =======================================

    @Test
    @DisplayName("Should return 400 when paymentId is null")
    void shouldReturn400WhenPaymentIdIsNull() throws Exception {
        // Arrange
        String invalidJson = """
            {
                "paymentId": null,
                "userId": "user_1",
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[*].field", hasItem("paymentId")));
    }

    @Test
    @DisplayName("Should return 400 when paymentId is blank")
    void shouldReturn400WhenPaymentIdIsBlank() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "   ",
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should return 400 when userId is null")
    void shouldReturn400WhenUserIdIsNull() throws Exception {
        // Arrange
        String invalidJson = """
            {
                "paymentId": "pay_1",
                "userId": null,
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field", hasItem("userId")));
    }

    @Test
    @DisplayName("Should return 400 when amount is null")
    void shouldReturn400WhenAmountIsNull() throws Exception {
        // Arrange
        String invalidJson = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": null,
                "currency": "USD"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field", hasItem("amount")));
    }

    @Test
    @DisplayName("Should return 400 when amount is zero")
    void shouldReturn400WhenAmountIsZero() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            BigDecimal.ZERO,
            "USD"
        );

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when amount is negative")
    void shouldReturn400WhenAmountIsNegative() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("-50.00"),
            "USD"
        );

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when currency is null")
    void shouldReturn400WhenCurrencyIsNull() throws Exception {
        // Arrange
        String invalidJson = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": 100.00,
                "currency": null
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field", hasItem("currency")));
    }

    @Test
    @DisplayName("Should return 400 when currency is not 3 characters")
    void shouldReturn400WhenCurrencyIsNot3Characters() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            "US"
        );

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when currency is not uppercase")
    void shouldReturn400WhenCurrencyIsNotUppercase() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            "usd"
        );

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 with multiple validation errors")
    void shouldReturn400WithMultipleValidationErrors() throws Exception {
        // Arrange
        String invalidJson = """
            {
                "paymentId": null,
                "userId": "",
                "amount": -10,
                "currency": "us"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors.length()", greaterThan(2)));
    }

    @Test
    @DisplayName("Should return 400 when request body is malformed JSON")
    void shouldReturn400WhenRequestBodyIsMalformedJson() throws Exception {
        // Arrange
        String malformedJson = "{ invalid json }";

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Malformed JSON")));
    }

    // =======================================
    //      DOMAIN EXCEPTIONS (HTTP 422)
    // =======================================

    @Test
    @DisplayName("Should return 422 when InvalidPaymentException is thrown")
    void shouldReturn422WhenInvalidPaymentExceptionIsThrown() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        when(mapper.toApprovePaymentCommand(any())).thenReturn(
            new ApprovePaymentCommand("pay_1", "user_1", new BigDecimal("100.00"), "USD")
        );
        when(approvePaymentUseCase.approvePayment(any()))
            .thenThrow(new InvalidPaymentException("Cannot approve a canceled payment"));

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
            .andExpect(jsonPath("$.message").value("Cannot approve a canceled payment"));
    }

    // =======================================
    //      RESOURCE NOT FOUND (HTTP 404)
    // =======================================

    @Test
    @DisplayName("Should return 404 when PaymentNotFoundException is thrown")
    void shouldReturn404WhenPaymentNotFoundExceptionIsThrown() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_999",
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        when(mapper.toApprovePaymentCommand(any())).thenReturn(
            new ApprovePaymentCommand("pay_999", "user_1", new BigDecimal("100.00"), "USD")
        );
        when(approvePaymentUseCase.approvePayment(any()))
            .thenThrow(new PaymentNotFoundException("pay_999"));

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message", containsString("pay_999")));
    }

    // =======================================
    //      UNEXPECTED ERRORS (HTTP 500)
    // =======================================

    @Test
    @DisplayName("Should return 500 when unexpected exception occurs")
    void shouldReturn500WhenUnexpectedExceptionOccurs() throws Exception {
        // Arrange
        PaymentApprovedRequestDto requestDto = new PaymentApprovedRequestDto(
            "pay_1",
            "user_1",
            new BigDecimal("100.00"),
            "USD"
        );

        when(mapper.toApprovePaymentCommand(any())).thenReturn(
            new ApprovePaymentCommand("pay_1", "user_1", new BigDecimal("100.00"), "USD")
        );
        when(approvePaymentUseCase.approvePayment(any()))
            .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(post("/api/payments/approved")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error").value("Internal Server Error"))
            .andExpect(jsonPath("$.message", containsString("unexpected error")));
    }
}
