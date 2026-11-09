package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.dto.AuthResponse;
import com.snovasys.ledgerline.dto.LoginRequest;
import com.snovasys.ledgerline.dto.RegisterRequest;
import com.snovasys.ledgerline.dto.UserResponse;
import com.snovasys.ledgerline.entity.AuditAction;
import com.snovasys.ledgerline.entity.AuditStatus;
import com.snovasys.ledgerline.entity.Role;
import com.snovasys.ledgerline.entity.User;
import com.snovasys.ledgerline.exception.BadRequestException;
import com.snovasys.ledgerline.exception.ResourceNotFoundException;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuthenticationManager authenticationManager,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        Role role = request.getRole() != null ? request.getRole() : Role.ROLE_ACCOUNT_HOLDER;

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setRole(role);
        user.setEnabled(true);

        User savedUser = userRepository.save(user);

        auditService.logAction(AuditAction.USER_REGISTER, savedUser.getUsername(), savedUser.getRole().name(),
                "USER", savedUser.getId().toString(), "User registration successful", AuditStatus.SUCCESS);

        String token = jwtTokenProvider.generateToken(savedUser.getUsername(), savedUser.getRole().name());

        return new AuthResponse(
                token,
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getRole(),
                jwtTokenProvider.getExpirationMs()
        );
    }

    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = userRepository.findByUsername(request.getUsername().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

            auditService.logAction(AuditAction.USER_LOGIN, user.getUsername(), user.getRole().name(),
                    "USER", user.getId().toString(), "User login successful", AuditStatus.SUCCESS);

            return new AuthResponse(
                    token,
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getRole(),
                    jwtTokenProvider.getExpirationMs()
            );
        } catch (Exception ex) {
            auditService.logAction(AuditAction.USER_LOGIN_FAILED, request.getUsername().trim(), "ANONYMOUS",
                    "USER", null, "Login failed: " + ex.getMessage(), AuditStatus.FAILURE);
            throw ex;
        }
    }

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadRequestException("No authenticated user found");
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return UserResponse.fromEntity(user);
    }
}

