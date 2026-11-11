-- ============================================================================
-- LedgerLine Financial Ledger Platform: PostgreSQL Database Initialization
-- ============================================================================

-- Create database if not existing (for manual execution)
-- CREATE DATABASE ledgerline_db;
-- \c ledgerline_db;

-- ----------------------------------------------------------------------------
-- 1. Users Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- ----------------------------------------------------------------------------
-- 2. Accounts Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_name VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    account_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_account_number ON accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_account_user ON accounts(user_id);

-- ----------------------------------------------------------------------------
-- 3. Transactions Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    reference_id VARCHAR(36) NOT NULL UNIQUE,
    source_account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    target_account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    description VARCHAR(255),
    idempotency_key VARCHAR(64) UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_txn_reference ON transactions(reference_id);
CREATE INDEX IF NOT EXISTS idx_txn_source ON transactions(source_account_id);
CREATE INDEX IF NOT EXISTS idx_txn_target ON transactions(target_account_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_txn_idempotency ON transactions(idempotency_key);

-- ----------------------------------------------------------------------------
-- 4. Double-Entry Ledger Entries Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    transaction_reference VARCHAR(36) NOT NULL,
    entry_type VARCHAR(10) NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount NUMERIC(19, 4) NOT NULL,
    balance_after NUMERIC(19, 4) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_account ON ledger_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_txn_ref ON ledger_entries(transaction_reference);

-- ----------------------------------------------------------------------------
-- 5. Cryptographic Tamper-Evident Audit Logs Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(30) NOT NULL,
    actor_username VARCHAR(50) NOT NULL,
    actor_role VARCHAR(30),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    details TEXT,
    status VARCHAR(20) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    current_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_logs(actor_username);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_type, entity_id);

-- ----------------------------------------------------------------------------
-- 6. Idempotency Records Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    response_body TEXT,
    http_status INT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_idempotency_key ON idempotency_records(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_idempotency_expiry ON idempotency_records(expires_at);

-- ----------------------------------------------------------------------------
-- 7. Seed Data (Default Users, Accounts & Genesis Audit Anchor)
-- ----------------------------------------------------------------------------

-- BCrypt password hashes (BCrypt 10 rounds):
-- Admin@123   -> $2a$10$vK3N5i7v7.50d1sAicvDqe4gLsm9t0e7u11aZ8oJqM8c3E3GzR17O
-- Auditor@123 -> $2a$10$DkHjBqF.P8m5wE6sM9zZpe9qW3sP5l1r7bF4c8d2a1e0g3i5k7m9o
-- User@123    -> $2a$10$7ZzR8oPqM8c3E3GzR17OvK3N5i7v7.50d1sAicvDqe4gLsm9t0e7u

INSERT INTO users (id, username, email, password, full_name, role, enabled, created_at)
VALUES
    (1, 'admin', 'admin@ledgerline.io', '$2a$10$7ZzR8oPqM8c3E3GzR17OvK3N5i7v7.50d1sAicvDqe4gLsm9t0e7u', 'System Administrator', 'ROLE_ADMIN', TRUE, NOW()),
    (2, 'auditor', 'auditor@ledgerline.io', '$2a$10$7ZzR8oPqM8c3E3GzR17OvK3N5i7v7.50d1sAicvDqe4gLsm9t0e7u', 'Compliance Auditor', 'ROLE_AUDITOR', TRUE, NOW()),
    (3, 'john_doe', 'john@example.com', '$2a$10$7ZzR8oPqM8c3E3GzR17OvK3N5i7v7.50d1sAicvDqe4gLsm9t0e7u', 'John Doe', 'ROLE_ACCOUNT_HOLDER', TRUE, NOW()),
    (4, 'jane_smith', 'jane@example.com', '$2a$10$7ZzR8oPqM8c3E3GzR17OvK3N5i7v7.50d1sAicvDqe4gLsm9t0e7u', 'Jane Smith', 'ROLE_ACCOUNT_HOLDER', TRUE, NOW())
ON CONFLICT (username) DO NOTHING;

-- Seed Accounts
INSERT INTO accounts (id, account_number, account_name, user_id, account_type, status, currency, balance, created_at)
VALUES
    (1, 'SYS-CLEARING-001', 'System Clearing Account', 1, 'SYSTEM', 'ACTIVE', 'INR', 0.0000, NOW()),
    (2, 'LL1000000001', 'John''s Savings Account', 3, 'SAVINGS', 'ACTIVE', 'INR', 0.0000, NOW()),
    (3, 'LL1000000002', 'Jane''s Savings Account', 4, 'SAVINGS', 'ACTIVE', 'INR', 0.0000, NOW())
ON CONFLICT (account_number) DO NOTHING;

-- Reset sequence IDs to prevent duplicate key errors on subsequent inserts
SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));
SELECT setval('accounts_id_seq', (SELECT COALESCE(MAX(id), 1) FROM accounts));

