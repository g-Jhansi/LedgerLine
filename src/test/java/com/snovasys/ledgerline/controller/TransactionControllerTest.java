package com.snovasys.ledgerline.controller;

import tools.jackson.databind.ObjectMapper;
import com.snovasys.ledgerline.dto.*;
import com.snovasys.ledgerline.entity.AccountType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Day 2 (Accounts, Double-Entry Ledger) and
 * Day 3 (Concurrency Control, Idempotency, Deposit/Withdraw/Transfer APIs).
 */
@SpringBootTest
@AutoConfigureMockMvc
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String johnToken;
    private String janeToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "Admin@123");
        johnToken = loginAndGetToken("john_doe", "User@123");
        janeToken = loginAndGetToken("jane_smith", "User@123");
    }

    // ─── Day 2: Account Tests ─────────────────────────────────────────

    @Test
    @DisplayName("D2.1 - Admin can create an account for a user")
    void adminCanCreateAccount() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountName("Test Checking Account");
        request.setOwnerUsername("john_doe");
        request.setAccountType(AccountType.CHECKING);
        request.setCurrency("INR");

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountName").value("Test Checking Account"))
                .andExpect(jsonPath("$.data.ownerUsername").value("john_doe"))
                .andExpect(jsonPath("$.data.accountType").value("CHECKING"))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("D2.2 - Non-admin cannot create accounts")
    void nonAdminCannotCreateAccount() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountName("Unauthorized Account");
        request.setOwnerUsername("john_doe");
        request.setAccountType(AccountType.SAVINGS);

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D2.3 - User can view their own accounts")
    void userCanViewOwnAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts/me")
                        .header("Authorization", "Bearer " + johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("D2.4 - User can view account details by account number")
    void userCanViewAccountDetails() throws Exception {
        mockMvc.perform(get("/api/accounts/LL1000000001")
                        .header("Authorization", "Bearer " + johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountNumber").value("LL1000000001"))
                .andExpect(jsonPath("$.data.ownerUsername").value("john_doe"));
    }

    // ─── Day 3: Deposit Tests ────────────────────────────────────────

    @Test
    @DisplayName("D3.1 - Deposit creates double-entry (DEBIT system + CREDIT user)")
    void depositShouldCreateDoubleEntry() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("LL1000000001");
        request.setAmount(new BigDecimal("5000.00"));
        request.setDescription("Initial deposit");

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionType").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.amount").value(5000.0))
                .andExpect(jsonPath("$.data.ledgerEntries").isArray())
                .andExpect(jsonPath("$.data.ledgerEntries.length()").value(2));
    }

    // ─── Day 3: Withdrawal Tests ────────────────────────────────────

    @Test
    @DisplayName("D3.2 - Withdrawal with sufficient balance succeeds")
    void withdrawalWithSufficientBalance() throws Exception {
        // First deposit
        DepositRequest deposit = new DepositRequest();
        deposit.setAccountNumber("LL1000000002");
        deposit.setAmount(new BigDecimal("10000.00"));
        deposit.setDescription("Pre-fund for withdrawal test");

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isCreated());

        // Then withdraw
        WithdrawRequest withdraw = new WithdrawRequest();
        withdraw.setAccountNumber("LL1000000002");
        withdraw.setAmount(new BigDecimal("3000.00"));
        withdraw.setDescription("Withdrawal test");

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdraw)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.amount").value(3000.0));
    }

    @Test
    @DisplayName("D3.3 - Withdrawal with insufficient balance returns 422")
    void withdrawalWithInsufficientBalanceShouldFail() throws Exception {
        WithdrawRequest request = new WithdrawRequest();
        request.setAccountNumber("LL1000000001");
        request.setAmount(new BigDecimal("99999999.00"));
        request.setDescription("Should fail - not enough balance");

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient Balance"));
    }

    // ─── Day 3: Transfer Tests ───────────────────────────────────────

    @Test
    @DisplayName("D3.4 - Transfer between accounts succeeds with double-entry")
    void transferBetweenAccountsShouldSucceed() throws Exception {
        // Fund john's account first
        DepositRequest deposit = new DepositRequest();
        deposit.setAccountNumber("LL1000000001");
        deposit.setAmount(new BigDecimal("20000.00"));

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isCreated());

        // Transfer john → jane
        TransferRequest transfer = new TransferRequest();
        transfer.setFromAccountNumber("LL1000000001");
        transfer.setToAccountNumber("LL1000000002");
        transfer.setAmount(new BigDecimal("7500.00"));
        transfer.setDescription("Payment to Jane");

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionType").value("TRANSFER"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.sourceAccountNumber").value("LL1000000001"))
                .andExpect(jsonPath("$.data.targetAccountNumber").value("LL1000000002"))
                .andExpect(jsonPath("$.data.ledgerEntries.length()").value(2));
    }

    @Test
    @DisplayName("D3.5 - Transfer to same account should fail")
    void transferToSameAccountShouldFail() throws Exception {
        TransferRequest transfer = new TransferRequest();
        transfer.setFromAccountNumber("LL1000000001");
        transfer.setToAccountNumber("LL1000000001");
        transfer.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isBadRequest());
    }

    // ─── Day 3: Idempotency Tests ───────────────────────────────────

    @Test
    @DisplayName("D3.6 - Same Idempotency-Key replays the cached response (no double deposit)")
    void idempotencyKeyShouldPreventDuplicateDeposit() throws Exception {
        String idempotencyKey = "test-idem-key-001";

        DepositRequest request = new DepositRequest();
        request.setAccountNumber("LL1000000001");
        request.setAmount(new BigDecimal("1000.00"));
        request.setDescription("Idempotent deposit");

        // First request — should succeed
        MvcResult firstResult = mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + johnToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.referenceId").exists())
                .andReturn();

        String firstReferenceId = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .get("data").get("referenceId").asText();

        // Second request with SAME key — should replay (same reference ID)
        MvcResult secondResult = mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + johnToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.referenceId").value(firstReferenceId))
                .andReturn();
    }

    // ─── Day 3: Transaction Lookup Tests ────────────────────────────

    @Test
    @DisplayName("D3.7 - Can retrieve transaction by reference ID")
    void canRetrieveTransactionByReference() throws Exception {
        // Create a deposit
        DepositRequest deposit = new DepositRequest();
        deposit.setAccountNumber("LL1000000001");
        deposit.setAmount(new BigDecimal("500.00"));

        MvcResult result = mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isCreated())
                .andReturn();

        String referenceId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("referenceId").asText();

        // Look it up
        mockMvc.perform(get("/api/transactions/" + referenceId)
                        .header("Authorization", "Bearer " + johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceId").value(referenceId))
                .andExpect(jsonPath("$.data.transactionType").value("DEPOSIT"));
    }

    @Test
    @DisplayName("D3.8 - Can retrieve account statement (ledger entries)")
    void canRetrieveAccountStatement() throws Exception {
        // Fund the account first
        DepositRequest deposit = new DepositRequest();
        deposit.setAccountNumber("LL1000000001");
        deposit.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deposit)))
                .andExpect(status().isCreated());

        // Get statement
        mockMvc.perform(get("/api/transactions/statement/LL1000000001")
                        .header("Authorization", "Bearer " + johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("D3.9 - Unauthenticated user cannot perform deposit")
    void unauthenticatedCannotDeposit() throws Exception {
        DepositRequest request = new DepositRequest();
        request.setAccountNumber("LL1000000001");
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ─── Helper ──────────────────────────────────────────────────────

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

