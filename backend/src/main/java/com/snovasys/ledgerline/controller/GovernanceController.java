package com.snovasys.ledgerline.controller;

import com.snovasys.ledgerline.dto.*;
import com.snovasys.ledgerline.entity.AuditAction;
import com.snovasys.ledgerline.service.AuditService;
import com.snovasys.ledgerline.service.GovernanceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Regulatory Governance & Compliance API.
 * Accessible strictly to Compliance Officers (ROLE_AUDITOR) and Administrators (ROLE_ADMIN).
 * Regular Account Holders (ROLE_ACCOUNT_HOLDER) receive 403 Forbidden.
 */
@RestController
@RequestMapping("/api/governance")
@PreAuthorize("hasAnyAuthority('ROLE_AUDITOR', 'ROLE_ADMIN')")
public class GovernanceController {

    private final AuditService auditService;
    private final GovernanceService governanceService;

    public GovernanceController(AuditService auditService, GovernanceService governanceService) {
        this.auditService = auditService;
        this.governanceService = governanceService;
    }

    /**
     * Regulatory Audit Logs endpoint with pagination and optional multi-attribute filtering.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogResponse> logs = auditService.getFilteredAuditLogs(actor, action, entityType, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved successfully", logs));
    }

    /**
     * Inspect single audit log entry by ID, including cryptographic hash link.
     */
    @GetMapping("/audit-logs/{id}")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getAuditLogById(@PathVariable Long id) {
        AuditLogResponse log = auditService.getAuditLogById(id);
        return ResponseEntity.ok(ApiResponse.success("Audit log entry retrieved", log));
    }

    /**
     * Cryptographic Audit Chain Integrity Verification.
     * Verifies block continuity and recalculates SHA-256 hashes across the entire audit log.
     */
    @GetMapping("/verify-chain")
    public ResponseEntity<ApiResponse<AuditChainIntegrityReport>> verifyAuditChain() {
        AuditChainIntegrityReport report = governanceService.verifyAuditChain();
        return ResponseEntity.ok(ApiResponse.success("Audit chain verification completed", report));
    }

    /**
     * Double-Entry Global Balance Conservation Invariant Verification.
     * Evaluates sum(debits) - sum(credits) == 0 across all ledger entries in the system.
     */
    @GetMapping("/verify-ledger")
    public ResponseEntity<ApiResponse<LedgerIntegrityReport>> verifyLedger() {
        LedgerIntegrityReport report = governanceService.verifyLedgerIntegrity();
        return ResponseEntity.ok(ApiResponse.success("Ledger integrity verification completed", report));
    }

    /**
     * High-level regulatory and compliance executive summary.
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<GovernanceSummaryResponse>> getGovernanceSummary() {
        GovernanceSummaryResponse summary = governanceService.getGovernanceSummary();
        return ResponseEntity.ok(ApiResponse.success("Governance summary retrieved", summary));
    }
}

