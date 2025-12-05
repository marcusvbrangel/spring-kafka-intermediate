package com.mvbr.store.event;

// userId = ordering key → garante ordering dentro da partição
// eventId → dá idempotência
// timestamp → ótima prática para rastreamento temporal
// Simples, enxuto, versionável

import java.math.BigDecimal;

public record PaymentApprovedEvent(
        String eventId,
        String paymentId,
        String userId,
        BigDecimal amount,
        String currency,
        String status,
        Long timestamp
) {}
