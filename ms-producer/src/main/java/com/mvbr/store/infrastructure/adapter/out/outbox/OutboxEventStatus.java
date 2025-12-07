package com.mvbr.store.infrastructure.adapter.out.outbox;

/**
 * Status do evento no Outbox Pattern.
 *
 * FLUXO:
 * PENDING → PUBLISHED (sucesso)
 * PENDING → FAILED (após max retries)
 */
public enum OutboxEventStatus {
    /**
     * Evento aguardando publicação no Kafka.
     */
    PENDING,

    /**
     * Evento publicado com sucesso no Kafka.
     */
    PUBLISHED,

    /**
     * Evento falhou após múltiplas tentativas.
     * Requer intervenção manual ou DLQ.
     */
    FAILED
}
