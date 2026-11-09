package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.dto.AuditChainIntegrityReport;
import com.snovasys.ledgerline.dto.AuditLogResponse;
import com.snovasys.ledgerline.entity.AuditAction;
import com.snovasys.ledgerline.entity.AuditLog;
import com.snovasys.ledgerline.entity.AuditStatus;
import com.snovasys.ledgerline.exception.ResourceNotFoundException;
import com.snovasys.ledgerline.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Service managing append-only, tamper-evident audit logs.
 * Implements cryptographic hash chaining: each audit entry links to the previous entry's SHA-256 hash.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final DateTimeFormatter HASH_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Appends an audit log entry in an independent transaction.
     * Computes the cryptographic hash linked to the previous log entry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized AuditLog logAction(AuditAction action,
                                            String actorUsername,
                                            String actorRole,
                                            String entityType,
                                            String entityId,
                                            String details,
                                            AuditStatus status) {
        String ipAddress = extractClientIp();
        String userAgent = extractUserAgent();
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        // 1. Retrieve the previous hash from the latest record
        Optional<AuditLog> latestRecord = auditLogRepository.findTopByOrderByIdDesc();
        String previousHash = latestRecord.map(AuditLog::getCurrentHash).orElse(GENESIS_HASH);

        // 2. Compute current record hash
        String currentHash = calculateHash(previousHash, action, actorUsername, entityType, entityId, details, status, now);

        // 3. Persist immutable record
        AuditLog auditLog = new AuditLog(
                action,
                actorUsername != null ? actorUsername : "ANONYMOUS",
                actorRole,
                ipAddress,
                userAgent,
                entityType,
                entityId,
                details,
                status != null ? status : AuditStatus.SUCCESS,
                previousHash,
                currentHash,
                now
        );

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Audit log #{} recorded: [{}] by [{}] (hash: {})",
                saved.getId(), action, actorUsername, currentHash.substring(0, 12));
        return saved;
    }

    /**
     * Query audit logs with optional filters and pagination.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getFilteredAuditLogs(String actor,
                                                        AuditAction action,
                                                        String entityType,
                                                        LocalDateTime startDate,
                                                        LocalDateTime endDate,
                                                        Pageable pageable) {
        return auditLogRepository.findFilteredAuditLogs(actor, action, entityType, startDate, endDate, pageable)
                .map(AuditLogResponse::fromEntity);
    }

    /**
     * Retrieve single audit log by ID.
     */
    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit record not found with id: " + id));
        return AuditLogResponse.fromEntity(auditLog);
    }

    /**
     * Verifies the cryptographic integrity of the entire audit chain.
     * Traverses every record from beginning to end and verifies:
     *  1. Record's previousHash == prior record's currentHash
     *  2. Record's currentHash == SHA-256(previousHash + payload)
     */
    @Transactional(readOnly = true)
    public AuditChainIntegrityReport verifyChainIntegrity() {
        List<AuditLog> chain = auditLogRepository.findAllByOrderByIdAsc();
        if (chain.isEmpty()) {
            return new AuditChainIntegrityReport(true, 0, null, "Chain is empty (no records to verify)");
        }

        String expectedPreviousHash = GENESIS_HASH;

        for (int i = 0; i < chain.size(); i++) {
            AuditLog entry = chain.get(i);

            // Check 1: previousHash must match expected link
            if (!expectedPreviousHash.equals(entry.getPreviousHash())) {
                String error = String.format("Broken chain at record ID %d: expected previous hash %s but found %s",
                        entry.getId(), expectedPreviousHash, entry.getPreviousHash());
                log.error(error);
                return new AuditChainIntegrityReport(false, i, entry.getId(), error);
            }

            // Check 2: currentHash must match recalculation of content
            String calculatedCurrent = calculateHash(
                    entry.getPreviousHash(),
                    entry.getAction(),
                    entry.getActorUsername(),
                    entry.getEntityType(),
                    entry.getEntityId(),
                    entry.getDetails(),
                    entry.getStatus(),
                    entry.getCreatedAt()
            );

            if (!calculatedCurrent.equals(entry.getCurrentHash())) {
                String error = String.format("Data tampering detected at record ID %d: computed hash %s does not match stored hash %s",
                        entry.getId(), calculatedCurrent, entry.getCurrentHash());
                log.error(error);
                return new AuditChainIntegrityReport(false, i, entry.getId(), error);
            }

            expectedPreviousHash = entry.getCurrentHash();
        }

        log.info("Audit chain successfully verified across {} records", chain.size());
        return new AuditChainIntegrityReport(true, chain.size(), null, "Cryptographic audit chain is valid and untampered");
    }

    /**
     * SHA-256 calculation forming the cryptographic block hash.
     */
    public String calculateHash(String previousHash,
                                AuditAction action,
                                String actorUsername,
                                String entityType,
                                String entityId,
                                String details,
                                AuditStatus status,
                                LocalDateTime createdAt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.join("|",
                    previousHash != null ? previousHash : GENESIS_HASH,
                    action != null ? action.name() : "",
                    actorUsername != null ? actorUsername : "",
                    entityType != null ? entityType : "",
                    entityId != null ? entityId : "",
                    details != null ? details : "",
                    status != null ? status.name() : "",
                    createdAt != null ? createdAt.format(HASH_TIMESTAMP_FORMATTER) : ""
            );
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    private String extractClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "127.0.0.1";
    }

    private String extractUserAgent() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String agent = request.getHeader("User-Agent");
            return agent != null ? agent : "SYSTEM";
        }
        return "SYSTEM";
    }
}
