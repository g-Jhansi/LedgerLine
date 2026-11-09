# LedgerLine - Safe Financial Ledger Platform

> **Hiring Challenge**: Solving unsafe money movement in financial applications with double-entry accounting, strict balance conservation, concurrency-safe locking, idempotency protection, and tamper-evident audit trails.

---

## 5-Day Development Roadmap

| Day | Focus Area | Status | Deliverables |
|:---:|:---|:---:|:---|
| **Day 1** | **Foundation, Security Architecture & User/Role Management (RBAC)** | **COMPLETED** | Spring Boot 4 / Java 21, SecurityFilterChain, JWT Authentication, Custom UserDetails, Roles (`ROLE_ADMIN`, `ROLE_ACCOUNT_HOLDER`, `ROLE_AUDITOR`), Auth Endpoints (`/register`, `/login`, `/me`, `/admin/users`), Health check (`/api/health`), Seed Users, Automated Auth Tests. |
| **Day 2** | **Account Domain, Double-Entry Ledger Core & Strict Balance Conservation** | **COMPLETED** | Account entity, Double-Entry Ledger entries (`Debit` = `Credit`), System Clearing Accounts (`SYS-CLEARING-001`), Zero-sum money movement invariant, Non-negative balance server checks, Statement API. |
| **Day 3** | **Concurrency Control, Simultaneous Withdrawal Safety & Idempotency Engine** | **COMPLETED** | Pessimistic Locking (`SELECT ... FOR UPDATE`), Deadlock-free ordered lock acquisition, Race condition elimination under high concurrent load, `Idempotency-Key` header & SHA-256 replay cache, Deposit / Withdraw / Transfer APIs. |
| **Day 4** | **Append-Only Immutable Audit Trail & Regulatory Governance APIs** | **COMPLETED** | Cryptographic tamper-evident Audit Log entity (blockchain-style SHA-256 hash chaining), Actor tracking, IP & User-Agent capture, Automated audit events on all mutations, Auditor inspection endpoints, Real-time ledger balance conservation verification ($\sum \text{Debits} - \sum \text{Credits} = 0$), Audit chain validation API, 32/32 Automated Tests Passing. |
| **Day 5** | **React Dashboard Integration, End-to-End Testing, Deployment & Submission Packaging** | Scheduled | React + Vite UI integration, Interactive dashboards for Account Holders, Auditors, and Admins, Docker configuration, Deployment guide, Six hiring questions documentation. |

---

## Day 1 Architecture & Implementation Details

### 1. Key Security & Integrity Decisions
- **Stateless JWT Security**: The API uses standard HMAC-SHA256 tokens (`JwtTokenProvider`) verified on every request through `JwtAuthenticationFilter`.
- **Role-Based Access Control (RBAC)**:
  - `ROLE_ADMIN`: Platform administrator with full access to user and account management.
  - `ROLE_AUDITOR`: Read-only compliance auditor with access to transaction statements, audit trails, and regulatory governance tools.
  - `ROLE_ACCOUNT_HOLDER`: End-user customer who owns accounts and initiates deposits, withdrawals, and transfers.
- **Password Protection**: Passwords are never stored in plaintext; all credentials are encrypted using `BCryptPasswordEncoder` with salted hashes.
- **Consistent API Contract**: Standardized JSON responses for both success (`ApiResponse<T>`) and error conditions (`GlobalExceptionHandler`), ensuring consistent HTTP status codes (`400`, `401`, `403`, `404`, `409`, `422`, `500`).
- **Data Seeding**: Automated `CommandLineRunner` (`DataInitializer`) seeds default user accounts and financial accounts on first startup.

---

## Day 2 Architecture & Implementation Details

### 1. Core Domain Entities
- **`Account`**: Represents financial accounts with unique 12-digit account numbers (e.g. `LL1000000001`), account types (`SAVINGS`, `CHECKING`, `SYSTEM`), currency (`INR`), and precision balance tracking (`BigDecimal(19, 4)`).
- **`LedgerEntry`**: Immutable double-entry bookkeeping records with `DEBIT` and `CREDIT` classifications, running balance after transaction (`balance_after`), and transaction references.
- **`Transaction`**: Financial unit of work grouping source account, target account, amount, status (`COMPLETED`, `PENDING`, `FAILED`), and audit metadata.

### 2. Double-Entry Accounting Core
- Every money movement produces **exactly two balanced entries**:
  - **DEPOSIT**: `DEBIT` on System Clearing Account (`SYS-CLEARING-001`), `CREDIT` on User Account.
  - **WITHDRAWAL**: `DEBIT` on User Account, `CREDIT` on System Clearing Account (`SYS-CLEARING-001`).
  - **TRANSFER**: `DEBIT` on Source Account, `CREDIT` on Target Account.
- **Conservation Invariant**: $\sum \text{Debits} - \sum \text{Credits} = 0$. Money is never created from nothing nor destroyed; system clearing accounts maintain the global zero-sum balance.
- **Non-Negative Balance Protection**: Pre-transaction checks enforce that user balances cannot drop below zero (`InsufficientBalanceException` $\rightarrow$ `422 Unprocessable Entity`).

---

## Day 3 Architecture & Implementation Details

### 1. Concurrency Control & Race Condition Safety
- **Pessimistic Write Locking (`SELECT ... FOR UPDATE`)**: 
  - Annotated on `AccountRepository.findByAccountNumberWithLock()`.
  - Row-level database locks ensure only one transaction can mutate an account's balance at any given millisecond, eliminating simultaneous withdrawal race conditions and double-spending.
- **Deadlock-Free Lock Acquisition**:
  - In transfers involving two accounts, locks are always acquired in deterministic lexicographical order (`accountNumber1.compareTo(accountNumber2) <= 0`).
  - Prevents circular dependency deadlocks when two users simultaneously transfer money to each other.

### 2. Idempotency Engine & Replay Protection
- **`Idempotency-Key` Header Support**: Clients can supply a unique UUID or token in the `Idempotency-Key` HTTP request header for all mutating endpoints (`/deposit`, `/withdraw`, `/transfer`).
- **Cryptographic Hash Verification**: The request payload is hashed using SHA-256 (`request_hash`). If the same key is supplied with conflicting parameters, the engine returns `409 Conflict` (`DuplicateTransactionException`).
- **Replay Cache**: Retried requests with identical keys and hashes return the cached transaction response without re-executing balance mutations.
- **Automatic TTL Cleanup**: Records expire after 24 hours and are cleaned up on a scheduled basis.

---

## Day 4 Architecture & Implementation Details

### 1. Tamper-Evident Cryptographic Audit Trail
- **Blockchain-Style Hash Chaining**: Every `AuditLog` entry contains:
  - `previousHash`: The SHA-256 hash of the immediate prior audit log entry (genesis block uses 64 zeros).
  - `currentHash`: Calculated as $\text{SHA-256}(\text{previousHash} \parallel \text{action} \parallel \text{actor} \parallel \text{entityType} \parallel \text{entityId} \parallel \text{details} \parallel \text{status} \parallel \text{timestamp})$.
- **Tamper Detection**: If any row is altered, inserted, or deleted in the database, the cryptographic link is severed. The verification algorithm scans the chain and identifies the exact tampered record ID and discrepancy.

### 2. Automated Event Capture & Actor Tracking
- Automated audit log generation runs across all state changes:
  - **Auth Events**: `USER_REGISTER`, `USER_LOGIN` (with `SUCCESS`), `USER_LOGIN_FAILED` (with `FAILURE`).
  - **Account Operations**: `ACCOUNT_CREATED`.
  - **Transaction Operations**: `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`.
  - **Compliance Operations**: `INTEGRITY_CHECK`.
- Captures full actor metadata:
  - `actorUsername`, `actorRole`
  - `ipAddress` (with reverse proxy and `X-Forwarded-For` resolution)
  - `userAgent` (client browser/device tracking)
  - `entityType`, `entityId`, descriptive details, and timestamp.

### 3. Regulatory Governance & Integrity APIs
- **Double-Entry Global Conservation Check (`GET /api/governance/verify-ledger`)**:
  - Calculates real-time $\sum \text{Debits} - \sum \text{Credits}$ across all ledger entries in the entire system.
  - Confirms global zero-sum money conservation.
- **Audit Chain Verification (`GET /api/governance/verify-chain`)**:
  - Validates cryptographic hash continuity across all historical audit records.
  - Reports whether the audit trail is valid or flags any tampered record.
- **Governance Executive Summary (`GET /api/governance/summary`)**:
  - Aggregates platform-wide metrics: total users, active accounts, completed transactions, audit log count, gross deposit/withdrawal/transfer volume, and real-time ledger & audit health indicators.

---

## Default Seed Credentials & Accounts

| Username | Password | Role | Account Number | Account Name | Initial Balance |
|:---|:---|:---|:---|:---|:---|
| `admin` | `Admin@123` | `ROLE_ADMIN` | `SYS-CLEARING-001` | System Clearing Account | `0.00 INR` |
| `auditor` | `Auditor@123` | `ROLE_AUDITOR` | — | Compliance Officer | — |
| `john_doe` | `User@123` | `ROLE_ACCOUNT_HOLDER` | `LL1000000001` | John's Savings Account | `0.00 INR` |
| `jane_smith` | `User@123` | `ROLE_ACCOUNT_HOLDER` | `LL1000000002` | Jane's Savings Account | `0.00 INR` |

---

## API Endpoints (Day 1 – Day 4)

### Authentication & User Management (Day 1)
| Method | Endpoint | Access | Description |
|:---|:---|:---|:---|
| `GET` | `/api/health` | Public | System and database health status |
| `POST` | `/api/auth/register` | Public | Register new user account |
| `POST` | `/api/auth/login` | Public | Authenticate credentials & return JWT |
| `GET` | `/api/auth/me` | Authenticated | Retrieve current user profile |
| `GET` | `/api/auth/admin/users` | `ROLE_ADMIN` | List all registered users |

### Account Management (Day 2)
| Method | Endpoint | Access | Description |
|:---|:---|:---|:---|
| `POST` | `/api/accounts` | `ROLE_ADMIN` | Create a new financial account for a user |
| `GET` | `/api/accounts/me` | `ROLE_ACCOUNT_HOLDER` | Retrieve accounts belonging to authenticated user |
| `GET` | `/api/accounts/{accountNumber}` | Authenticated | Retrieve specific account details |

### Financial Transactions & Concurrency Safety (Day 3)
| Method | Endpoint | Headers | Access | Description |
|:---|:---|:---|:---|:---|
| `POST` | `/api/transactions/deposit` | `Idempotency-Key` (opt) | `ACCOUNT_HOLDER`, `ADMIN` | Deposit money from system clearing account to user account |
| `POST` | `/api/transactions/withdraw` | `Idempotency-Key` (opt) | `ACCOUNT_HOLDER`, `ADMIN` | Withdraw money with non-negative balance check |
| `POST` | `/api/transactions/transfer` | `Idempotency-Key` (opt) | `ACCOUNT_HOLDER`, `ADMIN` | Deadlock-free account-to-account transfer |
| `GET` | `/api/transactions/{referenceId}` | Authenticated | Retrieve transaction and double-entry ledger lines |
| `GET` | `/api/transactions/account/{accountNumber}` | Authenticated | Paginated transaction history for an account |
| `GET` | `/api/transactions/statement/{accountNumber}` | Authenticated | Paginated double-entry statement (audit entries) |

### Regulatory Governance & Audit Trail (Day 4)
| Method | Endpoint | Access | Description |
|:---|:---|:---|:---|
| `GET` | `/api/governance/audit-logs` | `ROLE_AUDITOR`, `ROLE_ADMIN` | Paginated search of audit logs (filters: actor, action, entityType, date range) |
| `GET` | `/api/governance/audit-logs/{id}` | `ROLE_AUDITOR`, `ROLE_ADMIN` | Retrieve single audit log with cryptographic hash proof |
| `GET` | `/api/governance/verify-chain` | `ROLE_AUDITOR`, `ROLE_ADMIN` | Verify cryptographic hash chain integrity across all audit records |
| `GET` | `/api/governance/verify-ledger` | `ROLE_AUDITOR`, `ROLE_ADMIN` | Real-time validation that $\sum \text{Debits} - \sum \text{Credits} = 0$ |
| `GET` | `/api/governance/summary` | `ROLE_AUDITOR`, `ROLE_ADMIN` | Executive compliance summary (volumes, accounts, integrity status) |

---

## Automated Test Suite

LedgerLine includes an end-to-end suite of automated integration and unit tests covering Day 1 through Day 4:

```bash
mvn test
```

### Test Coverage Highlights (32/32 Passing)
- **`AuthControllerTest` (8 Tests)**:
  - Health check UP validation
  - User registration & JWT generation
  - Valid and invalid login authentication
  - Protected endpoint 401 Unauthorized handling
  - Profile retrieval (`/api/auth/me`)
  - Role-based authorization enforcement (`ROLE_ADMIN` vs `ROLE_ACCOUNT_HOLDER`)
- **`TransactionControllerTest` (13 Tests)**:
  - Admin account provisioning (`POST /api/accounts`)
  - RBAC protection forbidding non-admins from creating accounts
  - User account enumeration (`GET /api/accounts/me`)
  - Account detail lookup (`GET /api/accounts/{accountNumber}`)
  - Concurrency-safe deposit with double-entry validation (DEBIT clearing, CREDIT user)
  - Withdrawal with balance deduction and non-negative balance verification
  - Overdraft rejection (`422 Unprocessable Entity` on insufficient balance)
  - Inter-account transfers with balanced ledger entries
  - Self-transfer rejection (`400 Bad Request`)
  - Idempotent request replay protection (duplicate `Idempotency-Key` returns cached response without duplicate balance modification)
  - Transaction retrieval by reference ID
  - Paginated account statement inspection
  - Unauthenticated transaction rejection
- **`GovernanceControllerTest` (10 Tests)**:
  - Auditor paginated audit log retrieval (`GET /api/governance/audit-logs`)
  - Auditor single audit log inspection with cryptographic hash verification (`GET /api/governance/audit-logs/{id}`)
  - Cryptographic audit chain validation (`GET /api/governance/verify-chain`)
  - Real-time double-entry ledger balance conservation validation (`GET /api/governance/verify-ledger`)
  - Executive compliance dashboard metrics summary (`GET /api/governance/summary`)
  - Admin access validation for governance endpoints
  - RBAC protection forbidding Account Holders from accessing governance endpoints (`403 Forbidden`)
  - Unauthenticated request rejection (`401 Unauthorized`)
  - Automatic audit log creation upon transaction execution (Deposit $\rightarrow$ `AuditLog`)
  - Cryptographic tamper detection test: dynamically detects unauthorized database mutations and flags the exact corrupted record ID
- **`LedgerLineApplicationTests` (1 Test)**:
  - Spring Boot application context load verification

---

## How to Run the Application

### Prerequisites
- **Java 21 (JDK 21)**
- **Maven 3.9+** (or included `mvnw.cmd` / `mvnw`)
- **PostgreSQL 14+** (running on port `5432` with database `ledgerline_db`)

### 1. Database Setup
```sql
CREATE DATABASE ledgerline_db;
```

### 2. Start the Backend
Set your PostgreSQL password and run:
```cmd
set "DB_PASSWORD=YOUR_POSTGRES_PASSWORD"
mvn spring-boot:run
```
*(Or use `mvnw.cmd spring-boot:run`)*

### 3. Run Automated Tests
```cmd
mvn test
```
All tests run against the isolated in-memory H2 PostgreSQL-compatible test configuration.
