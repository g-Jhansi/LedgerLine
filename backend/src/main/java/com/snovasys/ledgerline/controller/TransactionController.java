package com.snovasys.ledgerline.controller;

import com.snovasys.ledgerline.dto.*;
import com.snovasys.ledgerline.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Transaction API endpoints for Deposit, Withdraw, and Transfer operations.
 * All mutating endpoints support the Idempotency-Key header for replay protection.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Deposit funds into an account.
     * System Clearing Account → Target Account (double-entry).
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasAnyAuthority('ROLE_ACCOUNT_HOLDER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse response = transactionService.deposit(request, idempotencyKey);
        return new ResponseEntity<>(ApiResponse.success("Deposit completed successfully", response), HttpStatus.CREATED);
    }

    /**
     * Withdraw funds from an account.
     * Source Account → System Clearing Account (double-entry).
     * Enforces non-negative balance.
     */
    @PostMapping("/withdraw")
    @PreAuthorize("hasAnyAuthority('ROLE_ACCOUNT_HOLDER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse response = transactionService.withdraw(request, idempotencyKey);
        return new ResponseEntity<>(ApiResponse.success("Withdrawal completed successfully", response), HttpStatus.CREATED);
    }

    /**
     * Transfer funds between two accounts.
     * Source Account → Target Account (double-entry).
     * Uses ordered lock acquisition to prevent deadlocks.
     */
    @PostMapping("/transfer")
    @PreAuthorize("hasAnyAuthority('ROLE_ACCOUNT_HOLDER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse response = transactionService.transfer(request, idempotencyKey);
        return new ResponseEntity<>(ApiResponse.success("Transfer completed successfully", response), HttpStatus.CREATED);
    }

    /**
     * Get transaction details by reference ID.
     */
    @GetMapping("/{referenceId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable String referenceId) {
        TransactionResponse response = transactionService.getTransactionByReference(referenceId);
        return ResponseEntity.ok(ApiResponse.success("Transaction retrieved", response));
    }

    /**
     * Get all transactions for a specific account (paginated).
     */
    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getAccountTransactions(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TransactionResponse> transactions = transactionService.getTransactionsByAccount(accountNumber, pageable);
        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved", transactions));
    }

    /**
     * Get account statement (ledger entries, paginated).
     */
    @GetMapping("/statement/{accountNumber}")
    public ResponseEntity<ApiResponse<Page<LedgerEntryResponse>>> getAccountStatement(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LedgerEntryResponse> statement = transactionService.getAccountStatement(accountNumber, pageable);
        return ResponseEntity.ok(ApiResponse.success("Account statement retrieved", statement));
    }
}

