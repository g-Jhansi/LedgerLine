package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.dto.AccountResponse;
import com.snovasys.ledgerline.dto.CreateAccountRequest;
import com.snovasys.ledgerline.entity.Account;
import com.snovasys.ledgerline.entity.AccountType;
import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.exception.BadRequestException;
import com.snovasys.ledgerline.exception.ResourceNotFoundException;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String SYSTEM_ACCOUNT_NUMBER = "SYS-CLEARING-001";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new account for a user (Admin only).
     * Generates a unique 12-digit account number.
     */
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        User owner = userRepository.findByUsername(request.getOwnerUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getOwnerUsername()));

        if (request.getAccountType() == AccountType.SYSTEM) {
            throw new BadRequestException("Cannot manually create SYSTEM accounts");
        }

        String accountNumber = generateAccountNumber();
        Account account = new Account(accountNumber, request.getAccountName(),
                owner, request.getAccountType(), request.getCurrency());

        Account saved = accountRepository.save(account);
        log.info("Created account [{}] for user [{}]", accountNumber, owner.getUsername());

        return AccountResponse.fromEntity(saved);
    }

    /**
     * Gets all accounts for the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .filter(a -> a.getAccountType() != AccountType.SYSTEM)
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Gets account details by account number.
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        return AccountResponse.fromEntity(account);
    }

    /**
     * Gets or creates the system clearing account used as the contra-entry
     * for deposits and withdrawals.
     */
    @Transactional
    public Account getOrCreateSystemAccount(User systemUser) {
        return accountRepository.findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                .orElseGet(() -> {
                    Account systemAccount = new Account(
                            SYSTEM_ACCOUNT_NUMBER,
                            "System Clearing Account",
                            systemUser,
                            AccountType.SYSTEM,
                            "INR"
                    );
                    log.info("Creating system clearing account [{}]", SYSTEM_ACCOUNT_NUMBER);
                    return accountRepository.save(systemAccount);
                });
    }

    /**
     * Gets the system clearing account number.
     */
    public String getSystemAccountNumber() {
        return SYSTEM_ACCOUNT_NUMBER;
    }

    /**
     * Generates a unique 12-digit account number prefixed with "LL".
     */
    private String generateAccountNumber() {
        String number;
        do {
            number = "LL" + String.format("%010d", Math.abs(UUID.randomUUID().hashCode()) % 10000000000L);
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}

