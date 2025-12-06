-- =============================================================================================
-- V1__create_initial_tables.sql
-- Creates initial tables for Payment and ProcessedEvent entities
-- Database: PostgreSQL
-- =============================================================================================

-- =============================================================================================
-- TABLE: payment
-- Stores payment transaction data
-- =============================================================================================
CREATE TABLE payment (
    payment_id VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL
);

-- Index for faster lookups by user
CREATE INDEX idx_payment_user_id ON payment(user_id);

-- Index for faster lookups by status
CREATE INDEX idx_payment_status ON payment(status);

-- Index for faster lookups by created_at (for time-based queries)
CREATE INDEX idx_payment_created_at ON payment(created_at);

-- =============================================================================================
-- TABLE: processed_events
-- Tracks processed Kafka events for idempotency
-- Prevents duplicate processing of the same event
-- =============================================================================================
CREATE TABLE processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(100),
    processed_at TIMESTAMP NOT NULL,
    kafka_partition INTEGER,
    kafka_offset BIGINT
);

-- Unique index on event_id (already enforced by UNIQUE constraint, but explicit for clarity)
CREATE UNIQUE INDEX idx_event_id ON processed_events(event_id);

-- Index for faster lookups by processed_at (for cleanup/retention policies)
CREATE INDEX idx_processed_at ON processed_events(processed_at);

-- Index for faster lookups by topic and event_type (for monitoring/debugging)
CREATE INDEX idx_topic_event_type ON processed_events(topic, event_type);
