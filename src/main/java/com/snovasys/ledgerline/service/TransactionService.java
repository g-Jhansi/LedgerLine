package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.dto.*;
import com.snovasys.ledgerline.entity.*;
import com.snovasys.ledgerline.exception.AccountNotActiveException;
import com.snovasys.ledgerline.exception.BadRequestException;
import com.snovasys.ledgerline.exception.InsufficientBalanceException;
import com.snovasys.ledgerline.exception.ResourceNotFoundException;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.LedgerEntryRepository;
import com.snovasys.ledgerline.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core transaction service implementing:
 * - Double-entry bookkeeping (every transaction produces DEBIT + CREDIT entries)
 * - Pessimistic locking (SELECT ... FOR UPDATE) for concurrency safety
 * - Ordered lock acquisition to prevent deadlocks on transfers
 * - Non-negative balance enforcement
 * - Idempotency-key support on all mutating operations
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountService accountService;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              LedgerEntryRepository ledgerEntryRepository,
                              AccountService accountService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountService = accountService;
    }

    /**
     * DEPOSIT: Move money from System Clearing Account → User Account.
     * Double-entry: DEBIT system (source of funds), CREDIT user (recipient).
     * Uses pessimistic locking on both accounts.
     */
    @Transactional
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {
        // Check idempotency at the transaction level
        if (idempotencyKey != null) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Replaying deposit for idempotency key [{}]", idempotencyKey);
                return buildTransactionResponse(existing.get());
            }
        }

        String systemAccountNumber = accountService.getSystemAccountNumber();
        String userAccountNumber = request.getAccountNumber();

        // Acquire locks in consistent order (lower account number first)
        Account[] locked = acquireLocksInOrder(systemAccountNumber, userAccountNumber);
        Account systemAccount = locked[0].getAccountNumber().equals(systemAccountNumber) ? locked[0] : locked[1];
        Account userAccount = locked[0].getAccountNumber().equals(userAccountNumber) ? locked[0] : locked[1];

        validateAccountActive(userAccount);

        BigDecimal amount = request.getAmount();
        String referenceId = UUID.randomUUID().toString();
        String description = request.getDescription() != null ? request.getDescription() : "Deposit";

        // Execute the double-entry
        // DEBIT the system account (money leaves the system source)
        systemAccount.setBalance(systemAccount.getBalance().subtract(amount));
        accountRepository.save(systemAccount);

        // CREDIT the user account (money enters user's account)
        userAccount.setBalance(userAccount.getBalance().add(amount));
        accountRepository.save(userAccount);

        // Create ledger entries
        LedgerEntry debitEntry = new LedgerEntry(systemAccount, referenceId, EntryType.DEBIT,
                amount, systemAccount.getBalance(), "DEPOSIT OUT: " + description);
        LedgerEntry creditEntry = new LedgerEntry(userAccount, referenceId, EntryType.CREDIT,
                amount, userAccount.getBalance(), "DEPOSIT IN: " + description);
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setReferenceId(referenceId);
        transaction.setSourceAccount(systemAccount);
        transaction.setTargetAccount(userAccount);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(description);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("DEPOSIT completed: {} {} → account [{}], ref [{}]",
                amount, userAccount.getCurrency(), userAccountNumber, referenceId);

        return buildTransactionResponse(transaction);
    }

    /**
     * WITHDRAWAL: Move money from User Account → System Clearing Account.
     * Double-entry: DEBIT user (funds leave), CREDIT system (funds received).
     * Enforces non-negative balance.
     */
    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request, String idempotencyKey) {
        // Check idempotency
        if (idempotencyKey != null) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Replaying withdrawal for idempotency key [{}]", idempotencyKey);
                return buildTransactionResponse(existing.get());
            }
        }

        String systemAccountNumber = accountService.getSystemAccountNumber();
        String userAccountNumber = request.getAccountNumber();

        // Acquire locks in consistent order
        Account[] locked = acquireLocksInOrder(systemAccountNumber, userAccountNumber);
        Account systemAccount = locked[0].getAccountNumber().equals(systemAccountNumber) ? locked[0] : locked[1];
        Account userAccount = locked[0].getAccountNumber().equals(userAccountNumber) ? locked[0] : locked[1];

        validateAccountActive(userAccount);

        BigDecimal amount = request.getAmount();

        // Non-negative balance check
        if (userAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available: " + userAccount.getBalance() + ", Requested: " + amount);
        }

        String referenceId = UUID.randomUUID().toString();
        String description = request.getDescription() != null ? request.getDescription() : "Withdrawal";

        // DEBIT the user account (money leaves)
        userAccount.setBalance(userAccount.getBalance().subtract(amount));
        accountRepository.save(userAccount);

        // CREDIT the system account (money received back)
        systemAccount.setBalance(systemAccount.getBalance().add(amount));
        accountRepository.save(systemAccount);

        // Create ledger entries
        LedgerEntry debitEntry = new LedgerEntry(userAccount, referenceId, EntryType.DEBIT,
                amount, userAccount.getBalance(), "WITHDRAWAL OUT: " + description);
        LedgerEntry creditEntry = new LedgerEntry(systemAccount, referenceId, EntryType.CREDIT,
                amount, systemAccount.getBalance(), "WITHDRAWAL IN: " + description);
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setReferenceId(referenceId);
        transaction.setSourceAccount(userAccount);
        transaction.setTargetAccount(systemAccount);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.WITHDRAWAL);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(description);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("WITHDRAWAL completed: {} {} from account [{}], ref [{}]",
                amount, userAccount.getCurrency(), userAccountNumber, referenceId);

        return buildTransactionResponse(transaction);
    }

    /**
     * TRANSFER: Move money from one User Account → another User Account.
     * Double-entry: DEBIT source (funds leave), CREDIT target (funds arrive).
     * Ordered lock acquisition prevents deadlocks.
     */
    @Transactional
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        // Check idempotency
        if (idempotencyKey != null) {
            var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Replaying transfer for idempotency key [{}]", idempotencyKey);
                return buildTransactionResponse(existing.get());
            }
        }

        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw new BadRequestException("Cannot transfer to the same account");
        }

        // Acquire locks in consistent order (prevents deadlocks)
        Account[] locked = acquireLocksInOrder(request.getFromAccountNumber(), request.getToAccountNumber());
        Account sourceAccount = locked[0].getAccountNumber().equals(request.getFromAccountNumber()) ? locked[0] : locked[1];
        Account targetAccount = locked[0].getAccountNumber().equals(request.getToAccountNumber()) ? locked[0] : locked[1];

        validateAccountActive(sourceAccount);
        validateAccountActive(targetAccount);

        BigDecimal amount = request.getAmount();

        // Non-negative balance check on source
        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Available: " + sourceAccount.getBalance() + ", Requested: " + amount);
        }

        String referenceId = UUID.randomUUID().toString();
        String description = request.getDescription() != null ? request.getDescription() : "Transfer";

        // DEBIT the source account
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        accountRepository.save(sourceAccount);

        // CREDIT the target account
        targetAccount.setBalance(targetAccount.getBalance().add(amount));
        accountRepository.save(targetAccount);

        // Create ledger entries
        LedgerEntry debitEntry = new LedgerEntry(sourceAccount, referenceId, EntryType.DEBIT,
                amount, sourceAccount.getBalance(), "TRANSFER OUT: " + description);
        LedgerEntry creditEntry = new LedgerEntry(targetAccount, referenceId, EntryType.CREDIT,
                amount, targetAccount.getBalance(), "TRANSFER IN: " + description);
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // Create transaction record
        Transaction transaction = new Transaction();
        transaction.setReferenceId(referenceId);
        transaction.setSourceAccount(sourceAccount);
        transaction.setTargetAccount(targetAccount);
        transaction.setAmount(amount);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(description);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info("TRANSFER completed: {} {} from [{}] → [{}], ref [{}]",
                amount, sourceAccount.getCurrency(),
                request.getFromAccountNumber(), request.getToAccountNumber(), referenceId);

        return buildTransactionResponse(transaction);
    }

    /**
     * Get transaction details by reference ID.
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByReference(String referenceId) {
        Transaction transaction = transactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + referenceId));
        return buildTransactionResponse(transaction);
    }

    /**
     * Get all transactions for an account (paginated).
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByAccount(String accountNumber, Pageable pageable) {
        // Verify account exists
        accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        return transactionRepository.findByAccountNumber(accountNumber, pageable)
                .map(this::buildTransactionResponse);
    }

    /**
     * Get account statement (paginated ledger entries).
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getAccountStatement(String accountNumber, Pageable pageable) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        return ledgerEntryRepository.findByAccountAccountNumberOrderByCreatedAtDesc(accountNumber, pageable)
                .map(LedgerEntryResponse::fromEntity);
    }

    // ─── Private helpers ──────────────────────────────────────────────

    /**
     * Acquires pessimistic locks on two accounts in a consistent order
     * (by account number, lexicographically) to prevent deadlocks.
     */
    private Account[] acquireLocksInOrder(String accountNumber1, String accountNumber2) {
        String first, second;
        if (accountNumber1.compareTo(accountNumber2) <= 0) {
            first = accountNumber1;
            second = accountNumber2;
        } else {
            first = accountNumber2;
            second = accountNumber1;
        }

        Account firstAccount = accountRepository.findByAccountNumberWithLock(first)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + first));
        Account secondAccount = accountRepository.findByAccountNumberWithLock(second)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + second));

        return new Account[]{firstAccount, secondAccount};
    }

    /**
     * Validates that an account is ACTIVE before allowing transactions.
     */
    private void validateAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Account " + account.getAccountNumber() + " is not active (status: " + account.getStatus() + ")");
        }
    }

    /**
     * Builds a TransactionResponse with associated ledger entries.
     */
    private TransactionResponse buildTransactionResponse(Transaction transaction) {
        TransactionResponse response = TransactionResponse.fromEntity(transaction);

        List<LedgerEntryResponse> entries = ledgerEntryRepository
                .findByTransactionReference(transaction.getReferenceId())
                .stream()
                .map(LedgerEntryResponse::fromEntity)
                .collect(Collectors.toList());
        response.setLedgerEntries(entries);

        return response;
    }
}

