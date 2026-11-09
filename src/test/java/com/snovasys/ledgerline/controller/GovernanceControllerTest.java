package com.snovasys.ledgerline.controller;

import tools.jackson.databind.ObjectMapper;
import com.snovasys.ledgerline.dto.DepositRequest;
import com.snovasys.ledgerline.dto.LoginRequest;
import com.snovasys.ledgerline.entity.AuditLog;
import com.snovasys.ledgerline.repository.AuditLogRepository;
import com.snovasys.ledgerline.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Day 4:
 * Append-Only Immutable Audit Trail & Regulatory Governance APIs.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class GovernanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    private String adminToken;
    private String auditorToken;
    private String johnToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "Admin@123");
        auditorToken = loginAndGetToken("auditor", "Auditor@123");
        johnToken = loginAndGetToken("john_doe", "User@123");
    }

    @Test
    @DisplayName("D4.1 - Auditor can retrieve audit logs with pagination")
    void auditorCanGetAuditLogs() throws Exception {
        mockMvc.perform(get("/api/governance/audit-logs")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("D4.2 - Auditor can inspect single audit log by ID")
    void auditorCanGetAuditLogById() throws Exception {
        // Ensure at least one log exists
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isNotEmpty();
        Long firstId = logs.get(0).getId();

        mockMvc.perform(get("/api/governance/audit-logs/" + firstId)
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(firstId))
                .andExpect(jsonPath("$.data.currentHash").exists())
                .andExpect(jsonPath("$.data.previousHash").exists());
    }

    @Test
    @DisplayName("D4.3 - Auditor can verify cryptographic hash chain integrity")
    void auditorCanVerifyAuditChain() throws Exception {
        mockMvc.perform(get("/api/governance/verify-chain")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chainValid").value(true))
                .andExpect(jsonPath("$.data.totalRecordsVerified").isNumber())
                .andExpect(jsonPath("$.data.tamperedRecordId").doesNotExist());
    }

    @Test
    @DisplayName("D4.4 - Auditor can verify double-entry global balance conservation")
    void auditorCanVerifyLedgerConservation() throws Exception {
        mockMvc.perform(get("/api/governance/verify-ledger")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.netImbalance").value(0.0));
    }

    @Test
    @DisplayName("D4.5 - Auditor can retrieve governance summary report")
    void auditorCanGetGovernanceSummary() throws Exception {
        mockMvc.perform(get("/api/governance/summary")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.totalAccounts").isNumber())
                .andExpect(jsonPath("$.data.ledgerConservationBalanced").value(true))
                .andExpect(jsonPath("$.data.auditChainTamperProof").value(true));
    }

    @Test
    @DisplayName("D4.6 - Admin can also access governance endpoints")
    void adminCanAccessGovernance() throws Exception {
        mockMvc.perform(get("/api/governance/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("D4.7 - Account holder is forbidden (403) from accessing governance endpoints")
    void accountHolderForbiddenFromGovernance() throws Exception {
        mockMvc.perform(get("/api/governance/summary")
                        .header("Authorization", "Bearer " + johnToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/governance/audit-logs")
                        .header("Authorization", "Bearer " + johnToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/governance/verify-chain")
                        .header("Authorization", "Bearer " + johnToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D4.8 - Unauthenticated request to governance returns 401 Unauthorized")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/governance/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("D4.9 - Transactions automatically append tamper-evident audit logs")
    void transactionCreatesAuditLog() throws Exception {
        long countBefore = auditLogRepository.count();

        DepositRequest deposit = new DepositRequest();
        deposit.setAccountNumber("LL1000000001");
        deposit.setAmount(new BigDecimal("250.00"));
        deposit.setDescription("Audit trail validation deposit");

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isCreated());

        long countAfter = auditLogRepository.count();
        assertThat(countAfter).isGreaterThan(countBefore);

        // Verify the chain remains valid after the new transaction
        var report = auditService.verifyChainIntegrity();
        assertThat(report.isChainValid()).isTrue();
    }

    @Test
    @DisplayName("D4.10 - Cryptographic hash chain detects tampering when record data is modified")
    void hashChainDetectsTampering() {
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isNotEmpty();

        AuditLog target = logs.get(logs.size() - 1);
        String originalDetails = target.getDetails();

        try {
            // Simulate retroactive database record tampering
            target.setDetails("TAMPERED_CONTENT_UNAUTHORIZED_MUTATION");
            auditLogRepository.save(target);

            // Audit chain verification must detect the discrepancy
            var report = auditService.verifyChainIntegrity();
            assertThat(report.isChainValid()).isFalse();
            assertThat(report.getTamperedRecordId()).isEqualTo(target.getId());
        } finally {
            // Restore integrity for other tests
            target.setDetails(originalDetails);
            auditLogRepository.save(target);
        }
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();
    }
}

