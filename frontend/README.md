# LedgerLine Frontend Dashboard

Standalone React 18 Single-Page Application (SPA) built with Vite and Tailwind CSS.

---

## Features
- **Quick-Role Switcher**: 1-click authentication as `ROLE_ADMIN` (`admin`), `ROLE_ACCOUNT_HOLDER` (`john_doe`), or `ROLE_AUDITOR` (`auditor`).
- **Live Account Balances**: Real-time balance cards with account types (`SAVINGS`, `CHECKING`, `SYSTEM`).
- **Financial Actions**:
  - Deposit funds from institutional clearing reserve.
  - Withdraw funds with server-side overdraft rejection.
  - Transfer funds with live debit and credit feedback.
  - **Idempotency Testing Toggle**: Replay identical `Idempotency-Key` headers to verify caching and zero duplicate mutations.
- **Double-Entry Statement Inspector**: Tabular view of immutable `DEBIT` and `CREDIT` line items.
- **Cryptographic Hash Chain Inspector**: Visual audit log blockchain with cryptographic link status.
- **Regulatory Governance Dashboard**: Solvency cards and zero-sum conservation verification.

---

## Quickstart Commands

### 1. Install Dependencies
```bash
npm install
```

### 2. Start Vite Development Server
```bash
npm run dev
```
*(Runs on `http://localhost:5173` and reverse-proxies `/api` calls to `http://localhost:8080`)*

### 3. Build Production Bundle
```bash
npm run build
```
*(Outputs optimized static bundle to `dist/`)*

