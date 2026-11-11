package com.snovasys.ledgerline.entity;

/**
 * Standardized audit action types tracked across the platform.
 */
public enum AuditAction {
    USER_REGISTER,
    USER_LOGIN,
    USER_LOGIN_FAILED,
    ACCOUNT_CREATED,
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    INTEGRITY_CHECK
}

