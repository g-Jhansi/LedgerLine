package com.snovasys.ledgerline.config;

import com.snovasys.ledgerline.entity.Role;
import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUserIfNotExists("admin", "admin@ledgerline.io", "Admin@123", "System Administrator", Role.ROLE_ADMIN);
        seedUserIfNotExists("auditor", "auditor@ledgerline.io", "Auditor@123", "Compliance Auditor", Role.ROLE_AUDITOR);
        seedUserIfNotExists("john_doe", "john@example.com", "User@123", "John Doe", Role.ROLE_ACCOUNT_HOLDER);
        seedUserIfNotExists("jane_smith", "jane@example.com", "User@123", "Jane Smith", Role.ROLE_ACCOUNT_HOLDER);
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
}

