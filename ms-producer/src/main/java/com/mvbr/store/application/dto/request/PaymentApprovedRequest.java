package com.mvbr.store.application.dto.request;

import java.math.BigDecimal;

public record PaymentApprovedRequest(
        String paymentId,
        String userId,
        BigDecimal amount,
        String currency
) {}
