# LedgerLine Backend Service

Spring Boot 4.1.2 / Java 21 REST API powering the LedgerLine financial ledger platform.

---

## Features
- **Stateless JWT Security & RBAC**: Admin, Auditor, and Account Holder roles.
- **Double-Entry Ledger Core**: Strict balance conservation ($\sum \text{Debits} - \sum \text{Credits} = 0$).
- **Pessimistic Write Locking**: `SELECT ... FOR UPDATE` with deterministic lexicographical ordering preventing simultaneous overdrafts and deadlocks.
- **Distributed Idempotency Engine**: SHA-256 payload fingerprinting and 24-hour replay caching.
- **Cryptographic Audit Trail**: Blockchain-style SHA-256 hash chaining with automated tamper detection.
- **Embedded Static Dashboard**: Pre-compiled React SPA served directly at `http://localhost:8080/index.html`.

---

## Quickstart Commands

### 1. Run Automated Test Suite (32 Tests)
```powershell
.\mvnw.cmd test
```

### 2. Run Backend Locally (Zero-Setup In-Memory Mode)
```powershell
.\mvnw.cmd spring-boot:run
```
*(Runs on `http://localhost:8080` with embedded React dashboard at `/index.html`)*

### 3. Run with PostgreSQL (Production Profile)
```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/ledgerline_db"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="yourpassword"
.\mvnw.cmd spring-boot:run
```

