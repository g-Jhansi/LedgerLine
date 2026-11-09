package com.snovasys.ledgerline.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * High-level compliance and governance dashboard summary for regulators and auditors.
 */
public class GovernanceSummaryResponse {

    private long totalUsers;
    private long totalAccounts;
    private long totalTransactions;
    private long totalAuditLogs;
    private BigDecimal totalDepositsVolume;
    private BigDecimal totalWithdrawalsVolume;
    private BigDecimal totalTransfersVolume;
    private boolean ledgerConservationBalanced;
    private boolean auditChainTamperProof;
    private LocalDateTime timestamp;

    public GovernanceSummaryResponse() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getTotalAccounts() {
        return totalAccounts;
    }

    public void setTotalAccounts(long totalAccounts) {
        this.totalAccounts = totalAccounts;
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public long getTotalAuditLogs() {
        return totalAuditLogs;
    }

    public void setTotalAuditLogs(long totalAuditLogs) {
        this.totalAuditLogs = totalAuditLogs;
    }

    public BigDecimal getTotalDepositsVolume() {
        return totalDepositsVolume;
    }

    public void setTotalDepositsVolume(BigDecimal totalDepositsVolume) {
        this.totalDepositsVolume = totalDepositsVolume;
    }

    public BigDecimal getTotalWithdrawalsVolume() {
        return totalWithdrawalsVolume;
    }

    public void setTotalWithdrawalsVolume(BigDecimal totalWithdrawalsVolume) {
        this.totalWithdrawalsVolume = totalWithdrawalsVolume;
    }

    public BigDecimal getTotalTransfersVolume() {
        return totalTransfersVolume;
    }

    public void setTotalTransfersVolume(BigDecimal totalTransfersVolume) {
        this.totalTransfersVolume = totalTransfersVolume;
    }

    public boolean isLedgerConservationBalanced() {
        return ledgerConservationBalanced;
    }

    public void setLedgerConservationBalanced(boolean ledgerConservationBalanced) {
        this.ledgerConservationBalanced = ledgerConservationBalanced;
    }

    public boolean isAuditChainTamperProof() {
        return auditChainTamperProof;
    }

    public void setAuditChainTamperProof(boolean auditChainTamperProof) {
        this.auditChainTamperProof = auditChainTamperProof;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

