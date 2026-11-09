package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.dto.AuditChainIntegrityReport;
import com.snovasys.ledgerline.dto.GovernanceSummaryResponse;
import com.snovasys.ledgerline.dto.LedgerIntegrityReport;
import com.snovasys.ledgerline.entity.AuditAction;
import com.snovasys.ledgerline.entity.AuditStatus;
import com.snovasys.ledgerline.entity.TransactionType;
import com.snovasys.ledgerline.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceService.class);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public GovernanceService(LedgerEntryRepository ledgerEntryRepository,
                             AccountRepository accountRepository,
                             TransactionRepository transactionRepository,
                             UserRepository userRepository,
                             AuditLogRepository auditLogRepository,
                             AuditService auditService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    /**
     * Verifies the double-entry accounting invariant:
     * sum(debits) - sum(credits) == 0.
     */
    @Transactional(readOnly = true)
    public LedgerIntegrityReport verifyLedgerIntegrity() {
        BigDecimal totalDebits = ledgerEntryRepository.sumTotalDebits();
        BigDecimal totalCredits = ledgerEntryRepository.sumTotalCredits();
        BigDecimal netImbalance = ledgerEntryRepository.calculateLedgerImbalance();
        if (netImbalance == null) {
            netImbalance = BigDecimal.ZERO;
        }

        boolean isBalanced = netImbalance.compareTo(BigDecimal.ZERO) == 0;
        long totalEntries = ledgerEntryRepository.count();
        long totalAccounts = accountRepository.count();

        // Audit the integrity check event
        auditEvent(AuditAction.INTEGRITY_CHECK, "LEDGER", "GLOBAL",
                "Double-entry check executed. Balanced: " + isBalanced + ", Imbalance: " + netImbalance,
                isBalanced ? AuditStatus.SUCCESS : AuditStatus.FAILURE);

        log.info("Ledger integrity verification: balanced={}, debits={}, credits={}, imbalance={}",
                isBalanced, totalDebits, totalCredits, netImbalance);

        return new LedgerIntegrityReport(isBalanced, totalDebits, totalCredits, netImbalance, totalEntries, totalAccounts);
    }

    /**
     * Verifies cryptographic audit chain integrity and records an audit event.
     */
    @Transactional(readOnly = true)
    public AuditChainIntegrityReport verifyAuditChain() {
        AuditChainIntegrityReport report = auditService.verifyChainIntegrity();

        auditEvent(AuditAction.INTEGRITY_CHECK, "AUDIT_CHAIN", "GLOBAL",
                "Audit chain verification executed. Valid: " + report.isChainValid() + ", Verified: " + report.getTotalRecordsVerified(),
                report.isChainValid() ? AuditStatus.SUCCESS : AuditStatus.FAILURE);

        return report;
    }

    /**
     * Generates a high-level governance and compliance summary.
     */
    @Transactional(readOnly = true)
    public GovernanceSummaryResponse getGovernanceSummary() {
        GovernanceSummaryResponse summary = new GovernanceSummaryResponse();

        summary.setTotalUsers(userRepository.count());
        summary.setTotalAccounts(accountRepository.count());
        summary.setTotalTransactions(transactionRepository.count());
        summary.setTotalAuditLogs(auditLogRepository.count());

        summary.setTotalDepositsVolume(transactionRepository.sumAmountByTransactionType(TransactionType.DEPOSIT));
        summary.setTotalWithdrawalsVolume(transactionRepository.sumAmountByTransactionType(TransactionType.WITHDRAWAL));
        summary.setTotalTransfersVolume(transactionRepository.sumAmountByTransactionType(TransactionType.TRANSFER));

        BigDecimal imbalance = ledgerEntryRepository.calculateLedgerImbalance();
        summary.setLedgerConservationBalanced(imbalance == null || imbalance.compareTo(BigDecimal.ZERO) == 0);

        AuditChainIntegrityReport chainReport = auditService.verifyChainIntegrity();
        summary.setAuditChainTamperProof(chainReport.isChainValid());

        return summary;
    }

    private void auditEvent(AuditAction action, String entityType, String entityId, String details, AuditStatus status) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";
            String role = (auth != null && !auth.getAuthorities().isEmpty()) ?
                    auth.getAuthorities().iterator().next().getAuthority() : "ROLE_SYSTEM";

            auditService.logAction(action, username, role, entityType, entityId, details, status);
        } catch (Exception ex) {
            log.warn("Could not log governance audit event: {}", ex.getMessage());
        }
    }
}

