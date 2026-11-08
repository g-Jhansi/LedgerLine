package com.snovasys.ledgerline.dto;

import com.snovasys.ledgerline.entity.EntryType;
import com.snovasys.ledgerline.entity.LedgerEntry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LedgerEntryResponse {

    private Long id;
    private String accountNumber;
    private String transactionReference;
    private EntryType entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime createdAt;

    public LedgerEntryResponse() {
    }

    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        LedgerEntryResponse response = new LedgerEntryResponse();
        response.setId(entry.getId());
        response.setAccountNumber(entry.getAccount().getAccountNumber());
        response.setTransactionReference(entry.getTransactionReference());
        response.setEntryType(entry.getEntryType());
        response.setAmount(entry.getAmount());
        response.setBalanceAfter(entry.getBalanceAfter());
        response.setDescription(entry.getDescription());
        response.setCreatedAt(entry.getCreatedAt());
        return response;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

