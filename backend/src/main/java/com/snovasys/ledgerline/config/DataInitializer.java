package com.snovasys.ledgerline.config;

import com.snovasys.ledgerline.entity.*;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;

    public DataInitializer(UserRepository userRepository, AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
    }

    @Override
    public void run(String... args) {
        // Seed users (Day 1)
        seedUserIfNotExists("admin", "admin@ledgerline.io", "Admin@123", "System Administrator", Role.ROLE_ADMIN);
        seedUserIfNotExists("auditor", "auditor@ledgerline.io", "Auditor@123", "Compliance Auditor", Role.ROLE_AUDITOR);
        seedUserIfNotExists("john_doe", "john@example.com", "User@123", "John Doe", Role.ROLE_ACCOUNT_HOLDER);
        seedUserIfNotExists("jane_smith", "jane@example.com", "User@123", "Jane Smith", Role.ROLE_ACCOUNT_HOLDER);

        // Seed system clearing account (Day 2) — requires the admin user as owner
        User adminUser = userRepository.findByUsername("admin").orElse(null);
        if (adminUser != null) {
            accountService.getOrCreateSystemAccount(adminUser);
        }

        // Seed default accounts for test users (Day 2)
        seedAccountIfNotExists("john_doe", "LL1000000001", "John's Savings Account", AccountType.SAVINGS);
        seedAccountIfNotExists("jane_smith", "LL1000000002", "Jane's Savings Account", AccountType.SAVINGS);
    }

    private void seedUserIfNotExists(String username, String email, String rawPassword, String fullName, Role role) {
        if (!userRepository.existsByUsername(username)) {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setFullName(fullName);
            user.setRole(role);
            user.setEnabled(true);
            userRepository.save(user);
            log.info("Initialized default seed user: [{}] with role [{}]", username, role);
        }
    }

    private void seedAccountIfNotExists(String username, String accountNumber, String accountName, AccountType type) {
        if (!accountRepository.existsByAccountNumber(accountNumber)) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                Account account = new Account(accountNumber, accountName, user, type, "INR");
                accountRepository.save(account);
                log.info("Initialized default seed account: [{}] for user [{}]", accountNumber, username);
            }
        }
    }
}
