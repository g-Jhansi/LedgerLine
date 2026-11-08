package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.entity.Account;
import com.snovasys.ledgerline.entity.AccountType;
import com.snovasys.ledgerline.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Pessimistic write lock: SELECT ... FOR UPDATE.
     * Used by TransactionService to prevent concurrent balance modifications.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberWithLock(@Param("accountNumber") String accountNumber);

    List<Account> findByUser(User user);

    List<Account> findByUserId(Long userId);

    Optional<Account> findByAccountType(AccountType accountType);

    boolean existsByAccountNumber(String accountNumber);
}

