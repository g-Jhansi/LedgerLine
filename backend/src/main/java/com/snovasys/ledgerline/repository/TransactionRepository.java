package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceId(String referenceId);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.accountNumber = :accountNumber " +
           "OR t.targetAccount.accountNumber = :accountNumber ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountNumber(@Param("accountNumber") String accountNumber, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), CAST(0 AS java.math.BigDecimal)) FROM Transaction t WHERE t.transactionType = :type AND t.status = 'COMPLETED'")
    java.math.BigDecimal sumAmountByTransactionType(@Param("type") com.snovasys.ledgerline.entity.TransactionType type);
}

