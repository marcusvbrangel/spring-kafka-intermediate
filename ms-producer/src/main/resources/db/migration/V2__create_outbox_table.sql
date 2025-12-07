-- ============================================================
-- V2: Outbox Pattern - Tabela de eventos pendentes
-- ============================================================
--
-- OUTBOX PATTERN:
-- Esta tabela armazena eventos que precisam ser publicados no Kafka.
-- Garante consistência entre banco de dados e mensageria (dual-write problem).
--
-- FLUXO:
-- 1. Transação salva Payment + OutboxEvent (atomicamente)
-- 2. Job assíncrono busca eventos PENDING
-- 3. Publica no Kafka
-- 4. Marca como PUBLISHED
--
-- IMPORTANTE:
-- - Índice em (status, created_at) para queries rápidas
-- - Partition por aggregate_type para melhor performance
-- - TTL/cleanup de eventos antigos (implementar depois)
-- ============================================================

CREATE TABLE outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,           -- Ex: 'PAYMENT', 'ORDER'
    aggregate_id VARCHAR(36) NOT NULL,             -- ID da entidade (payment_id)
    event_type VARCHAR(100) NOT NULL,              -- Ex: 'PAYMENT_APPROVED', 'PAYMENT_CANCELED'
    topic VARCHAR(100) NOT NULL,                   -- Tópico Kafka de destino
    partition_key VARCHAR(100) NOT NULL,           -- Chave de particionamento (userId)
    payload TEXT NOT NULL,                         -- JSON do evento
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PUBLISHED, FAILED
    retry_count INT NOT NULL DEFAULT 0,            -- Contador de tentativas
    error_message TEXT,                            -- Mensagem de erro (se houver)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    version INT NOT NULL DEFAULT 1                 -- Para versionamento de schema
);

-- ============================================================
-- ÍNDICES para performance
-- ============================================================

-- Índice principal: buscar eventos pendentes ordenados por data
CREATE INDEX idx_outbox_status_created
ON outbox_event(status, created_at);

-- Índice para buscar eventos por agregado (debugging/troubleshooting)
CREATE INDEX idx_outbox_aggregate
ON outbox_event(aggregate_type, aggregate_id);

-- Índice para eventos falhados (monitoring)
CREATE INDEX idx_outbox_failed
ON outbox_event(status, retry_count)
WHERE status = 'FAILED';

-- ============================================================
-- COMENTÁRIOS nas colunas
-- ============================================================

COMMENT ON TABLE outbox_event IS 'Outbox Pattern - Eventos pendentes para publicação no Kafka';

COMMENT ON COLUMN outbox_event.id IS 'UUID único do evento outbox';
COMMENT ON COLUMN outbox_event.aggregate_type IS 'Tipo do agregado (PAYMENT, ORDER, etc)';
COMMENT ON COLUMN outbox_event.aggregate_id IS 'ID da entidade de negócio';
COMMENT ON COLUMN outbox_event.event_type IS 'Tipo do evento de domínio';
COMMENT ON COLUMN outbox_event.topic IS 'Tópico Kafka onde o evento será publicado';
COMMENT ON COLUMN outbox_event.partition_key IS 'Chave de particionamento Kafka (ex: userId)';
COMMENT ON COLUMN outbox_event.payload IS 'JSON do evento serializado';
COMMENT ON COLUMN outbox_event.status IS 'Status do evento: PENDING, PUBLISHED, FAILED';
COMMENT ON COLUMN outbox_event.retry_count IS 'Número de tentativas de publicação';
COMMENT ON COLUMN outbox_event.error_message IS 'Mensagem de erro da última falha';
COMMENT ON COLUMN outbox_event.created_at IS 'Timestamp de criação do evento';
COMMENT ON COLUMN outbox_event.published_at IS 'Timestamp de publicação bem-sucedida';
COMMENT ON COLUMN outbox_event.version IS 'Versão do schema do evento';
