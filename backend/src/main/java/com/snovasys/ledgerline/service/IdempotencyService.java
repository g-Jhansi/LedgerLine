package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.entity.IdempotencyRecord;
import com.snovasys.ledgerline.exception.DuplicateTransactionException;
import com.snovasys.ledgerline.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Idempotency engine that prevents duplicate transaction processing.
 * When a client sends a request with an Idempotency-Key header:
 *   1. If the key exists and the request hash matches → return cached response
 *   2. If the key exists but the request hash differs → reject (409 Conflict)
 *   3. If the key is new → proceed with the operation, then cache the response
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final long EXPIRY_HOURS = 24;

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    /**
     * Check if an idempotency key already exists and return the cached record.
     * If the key exists but the request hash doesn't match, throws DuplicateTransactionException.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> checkIdempotency(String idempotencyKey, String requestBody) {
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            // Check if the record has expired
            if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.info("Idempotency key [{}] has expired, allowing re-use", idempotencyKey);
                return Optional.empty();
            }

            // Verify request hash matches
            String currentHash = hashRequest(requestBody);
            if (!record.getRequestHash().equals(currentHash)) {
                throw new DuplicateTransactionException(
                        "Idempotency key '" + idempotencyKey + "' was already used with a different request payload");
            }

            log.info("Replaying cached response for idempotency key [{}]", idempotencyKey);
            return existing;
        }

        return Optional.empty();
    }

    /**
     * Stores the response for a successfully processed idempotent request.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeResponse(String idempotencyKey, String requestBody, String responseBody, int httpStatus) {
        // Delete any expired record with same key
        idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> idempotencyRecordRepository.delete(record));

        IdempotencyRecord record = new IdempotencyRecord(
                idempotencyKey,
                hashRequest(requestBody),
                responseBody,
                httpStatus,
                LocalDateTime.now().plusHours(EXPIRY_HOURS)
        );
        idempotencyRecordRepository.save(record);
        log.info("Cached response for idempotency key [{}], expires in {} hours", idempotencyKey, EXPIRY_HOURS);
    }

    /**
     * Cleanup expired idempotency records (runs every hour).
     * Conditional on scheduling being enabled (disabled in test profiles).
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupExpiredRecords() {
        try {
            int deleted = idempotencyRecordRepository.deleteExpiredRecords(LocalDateTime.now());
            if (deleted > 0) {
                log.info("Cleaned up {} expired idempotency records", deleted);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup expired idempotency records: {}", e.getMessage());
        }
    }

    /**
     * SHA-256 hash of the request body for comparison.
     */
    private String hashRequest(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

