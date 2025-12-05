package com.mvbr.store.dto;

import java.math.BigDecimal;

public record PaymentApprovedRequest(
        String paymentId,
        String userId,
        BigDecimal amount,
        String currency
) {}
