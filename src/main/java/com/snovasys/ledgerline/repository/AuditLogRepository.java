package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.entity.AuditAction;
import com.snovasys.ledgerline.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Gets the most recent audit record to link the hash chain.
     */
    Optional<AuditLog> findTopByOrderByIdDesc();

    /**
     * Retrieves all logs in ascending sequence for chain verification.
     */
    List<AuditLog> findAllByOrderByIdAsc();

    /**
     * Filtered audit logs query with optional parameters and pagination.
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:actor IS NULL OR LOWER(a.actorUsername) = LOWER(:actor)) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findFilteredAuditLogs(
            @Param("actor") String actor,
            @Param("action") AuditAction action,
            @Param("entityType") String entityType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}

