package com.snovasys.ledgerline.entity;

/**
 * Types of financial accounts in the LedgerLine system.
 * SYSTEM accounts serve as clearing/contra accounts for deposits and withdrawals.
 */
public enum AccountType {
    SAVINGS,
    CHECKING,
    SYSTEM
}

