package com.snovasys.ledgerline.entity;

/**
 * Double-entry bookkeeping entry type.
 * Every transaction must produce exactly one DEBIT and one CREDIT entry
 * such that sum(debits) == sum(credits).
 */
public enum EntryType {
    DEBIT,
    CREDIT
}

