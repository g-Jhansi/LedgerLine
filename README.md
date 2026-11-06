# LedgerLine - Safe Financial Ledger Platform

> **Hiring Challenge**: Solving unsafe money movement in financial applications with double-entry accounting, strict balance conservation, concurrency-safe locking, idempotency protection, and tamper-evident audit trails.

---

## 5-Day Development Roadmap

| Day | Focus Area | Status | Deliverables |
|:---:|:---|:---:|:---|
| **Day 1** | **Foundation, Security Architecture & User/Role Management (RBAC)** | **COMPLETED** | Spring Boot 4 / Java 21, SecurityFilterChain, JWT Authentication, Custom UserDetails, Roles (`ROLE_ADMIN`, `ROLE_ACCOUNT_HOLDER`, `ROLE_AUDITOR`), Auth Endpoints (`/register`, `/login`, `/me`, `/admin/users`), Health check (`/api/health`), Seed Users, Automated Auth Tests. |
| **Day 2** | **Account Domain, Double-Entry Ledger Core & Strict Balance Conservation** | Scheduled | Account entity, Double-Entry Ledger entries (`Debit` = `Credit`), System Clearing Accounts, Zero-sum money movement invariant, Non-negative balance server checks, Statement API. |
| **Day 3** | **Concurrency Control, Simultaneous Withdrawal Safety & Idempotency Engine** | Scheduled | Pessimistic Locking (`SELECT ... FOR UPDATE`), Race condition elimination under high concurrent load, `Idempotency-Key` header interceptor & replay cache, Deposit / Withdraw / Transfer APIs. |
| **Day 4** | **Append-Only Immutable Audit Trail & Regulatory Governance APIs** | Scheduled | Tamper-evident Audit Log entity, Actor tracking, Timestamps, IP capture, Auditor & Admin inspection endpoints, System integrity verification (`sum(debits) - sum(credits) == 0`). |
| **Day 5** | **React Dashboard Integration, End-to-End Testing, Deployment & Submission Packaging** | Scheduled | React + Vite UI integration, Interactive dashboards for Account Holders, Auditors, and Admins, Docker configuration, Deployment guide, Six hiring questions documentation. |

---

## Day 1 Architecture & Implementation Details

### 1. Key Security & Integrity Decisions
- **Stateless JWT Security**: The API uses standard HMAC-SHA256 tokens (`JwtTokenProvider`) verified on every request through `JwtAuthenticationFilter`.
- **Role-Based Access Control (RBAC)**:
  - `ROLE_ADMIN`: Platform administrator with full access to user management and system metrics.
  - `ROLE_AUDITOR`: Read-only compliance auditor with access to transaction statements and audit trails.
  - `ROLE_ACCOUNT_HOLDER`: End-user customer who owns accounts and performs authorized transactions.
- **Password Protection**: Passwords are never stored in plaintext; all credentials are encrypted using `BCryptPasswordEncoder` with salted hashes.
- **Consistent API Contract**: Standardized JSON responses for both success (`ApiResponse<T>`) and error conditions (`GlobalExceptionHandler`), ensuring consistent HTTP status codes (`400`, `401`, `403`, `404`, `500`).
- **Data Seeding**: An automated `CommandLineRunner` (`DataInitializer`) seeds default user accounts on first startup so reviewers and developers can test immediately without manual SQL scripts.

---

## Default Seed Credentials (Day 1)

| Username | Password | Role | Description |
|:---|:---|:---|:---|
| `admin` | `Admin@123` | `ROLE_ADMIN` | System Administrator |
| `auditor` | `Auditor@123` | `ROLE_AUDITOR` | Compliance Officer / Auditor |
| `john_doe` | `User@123` | `ROLE_ACCOUNT_HOLDER` | Test Account Holder 1 |
| `jane_smith` | `User@123` | `ROLE_ACCOUNT_HOLDER` | Test Account Holder 2 |

---

## API Endpoints (Day 1)

| Method | Endpoint | Access | Description |
|:---|:---|:---|:---|
| `GET` | `/api/health` | Public | System and database health status |
| `POST` | `/api/auth/register` | Public | Register new user account |
| `POST` | `/api/auth/login` | Public | Authenticate credentials & return JWT |
| `GET` | `/api/auth/me` | Authenticated | Retrieve current user profile |
| `GET` | `/api/auth/admin/users` | `ROLE_ADMIN` | List all registered users |

---

## How to Run Day 1

### Prerequisites
- **Java 21 (JDK 21)**
- **Maven 3.9+** (or included `mvnw.cmd` / `mvnw`)
- **PostgreSQL 14+** (running on port `5432` with database `ledgerline_db`)

### 1. Database Setup
Create database in PostgreSQL:
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
All integration and security tests run against the automated in-memory test configuration.

