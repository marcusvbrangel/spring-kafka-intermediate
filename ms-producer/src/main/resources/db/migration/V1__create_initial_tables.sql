-- =============================================================================================
-- V1__create_initial_tables.sql
-- Creates initial tables for Payment entity (Producer only)
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
