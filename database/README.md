# LedgerLine: Database Architecture & Schema Documentation

This directory contains the database initialization scripts and relational architecture documentation for **LedgerLine**.

---

## 1. Directory Contents
- **`init.sql`**: Production-ready PostgreSQL DDL and seeding script. Automatically mounted in Docker Compose at `/docker-entrypoint-initdb.d/init.sql`.
- **`README.md`**: Complete schema reference and design principles.

---

## 2. Relational Schema Architecture

```mermaid
erDiagram
    USERS ||--o{ ACCOUNTS : owns
    ACCOUNTS ||--o{ TRANSACTIONS : "source / target"
    TRANSACTIONS ||--|{ LEDGER_ENTRIES : contains
    ACCOUNTS ||--o{ LEDGER_ENTRIES : modifies
    USERS ||--o{ AUDIT_LOGS : performs

    USERS {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password
        varchar full_name
        varchar role
        boolean enabled
        timestamp created_at
    }

    ACCOUNTS {
        bigint id PK
        varchar account_number UK
        varchar account_name
        bigint user_id FK
        varchar account_type
        varchar status
        varchar currency
        numeric balance
        timestamp created_at
    }

    TRANSACTIONS {
        bigint id PK
        varchar reference_id UK
        bigint source_account_id FK
        bigint target_account_id FK
        numeric amount
        varchar transaction_type
        varchar status
        varchar idempotency_key UK
        timestamp created_at
    }

    LEDGER_ENTRIES {
        bigint id PK
        bigint account_id FK
        varchar transaction_reference
        varchar entry_type
        numeric amount
        numeric balance_after
        timestamp created_at
    }

    AUDIT_LOGS {
        bigint id PK
        varchar action
        varchar actor_username
        varchar actor_role
        varchar ip_address
        varchar entity_type
        varchar entity_id
        text details
        varchar status
        varchar previous_hash
        varchar current_hash
        timestamp created_at
    }

    IDEMPOTENCY_RECORDS {
        bigint id PK
        varchar idempotency_key UK
        varchar request_hash
        text response_body
        int http_status
        timestamp expires_at
    }
```

---

## 3. Core Database Invariants

1. **Double-Entry Balance Conservation**:
   Every transaction writes at least two rows to `ledger_entries`: one `DEBIT` and one `CREDIT`.
   $$\sum \text{Debits} - \sum \text{Credits} = 0$$

2. **Pessimistic Row-Level Locking**:
   The `accounts` table uses `SELECT ... FOR UPDATE` exclusively within `@Transactional` blocks to ensure mutual exclusion during simultaneous balance mutations.

3. **Blockchain Hash Chaining**:
   The `audit_logs` table forms a cryptographic chain where `current_hash` is computed as:
   $$H_n = \text{SHA-256}(H_{n-1} \parallel \text{action} \parallel \text{actor} \parallel \text{entityType} \parallel \text{entityId} \parallel \text{details} \parallel \text{status} \parallel \text{timestamp})$$

---

## 4. How to Connect / Initialize Locally

### Via Docker Compose
When running `docker compose up`, PostgreSQL initializes automatically from `init.sql`:
```bash
docker compose up postgres
```

### Manual PostgreSQL Connection
```bash
psql -U postgres -h localhost -p 5432 -d ledgerline_db -f init.sql
```

