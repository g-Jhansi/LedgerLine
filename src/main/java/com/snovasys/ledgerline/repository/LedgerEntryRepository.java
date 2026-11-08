package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    List<LedgerEntry> findByTransactionReference(String transactionReference);

    Page<LedgerEntry> findByAccountAccountNumberOrderByCreatedAtDesc(String accountNumber, Pageable pageable);

    /**
     * Verify the double-entry invariant: sum of all debits must equal sum of all credits.
     * Returns 0 if the ledger is balanced.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = 'DEBIT' THEN e.amount ELSE CAST(0 AS java.math.BigDecimal) END), CAST(0 AS java.math.BigDecimal)) - " +
           "COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE CAST(0 AS java.math.BigDecimal) END), CAST(0 AS java.math.BigDecimal)) " +
           "FROM LedgerEntry e")
    BigDecimal calculateLedgerImbalance();
}

