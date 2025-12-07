package com.mvbr.store.infrastructure.adapter.out.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository para OutboxEvent.
 *
 * QUERIES CRÍTICAS:
 * - findPendingEvents(): busca eventos para processar
 * - findOldPublishedEvents(): busca eventos para cleanup
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * Busca eventos PENDING ordenados por data de criação.
     *
     * USADO POR: OutboxPublisher (scheduled job)
     *
     * PERFORMANCE:
     * - Usa índice idx_outbox_status_created
     * - Limit evita carregar milhões de registros
     *
     * @param status status do evento
     * @param limit máximo de eventos a buscar
     * @return lista de eventos pendentes
     */
    @Query(value = "SELECT e FROM OutboxEvent e " +
           "WHERE e.status = :status " +
           "ORDER BY e.createdAt ASC " +
           "LIMIT :limit")
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            @Param("status") OutboxEventStatus status,
            @Param("limit") int limit
    );

    /**
     * Busca eventos PENDING (sem limit).
     * Usar com cuidado!
     *
     * @return lista de eventos pendentes
     */
    List<OutboxEvent> findByStatus(OutboxEventStatus status);

    /**
     * Busca eventos PUBLISHED antigos para cleanup.
     *
     * USADO POR: Job de limpeza (implementar depois)
     *
     * @param status status
     * @param publishedBefore data limite
     * @return eventos antigos publicados
     */
    List<OutboxEvent> findByStatusAndPublishedAtBefore(
            OutboxEventStatus status,
            Instant publishedBefore
    );

    /**
     * Busca eventos FAILED para monitoramento/alertas.
     *
     * @return eventos falhados
     */
    List<OutboxEvent> findByStatusAndRetryCountGreaterThan(
            OutboxEventStatus status,
            Integer retryCount
    );

    /**
     * Conta eventos por status (para métricas).
     *
     * @param status status
     * @return quantidade de eventos
     */
    long countByStatus(OutboxEventStatus status);

    /**
     * Busca eventos de um agregado específico (debugging).
     *
     * @param aggregateType tipo do agregado
     * @param aggregateId ID do agregado
     * @return eventos do agregado
     */
    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            String aggregateType,
            String aggregateId
    );
}
