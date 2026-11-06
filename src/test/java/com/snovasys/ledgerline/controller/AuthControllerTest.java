package com.snovasys.ledgerline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snovasys.ledgerline.dto.LoginRequest;
import com.snovasys.ledgerline.dto.RegisterRequest;
import com.snovasys.ledgerline.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("1. Health endpoint should return UP status")
    void healthCheckShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("LedgerLine-Backend"))
                .andExpect(jsonPath("$.databaseConnected").value(true));
    }

    @Test
    @DisplayName("2. User registration should return 201 and JWT token")
    void registerShouldSucceed() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "alice_test",
                "alice@example.com",
                "Secret@123",
                "Alice Wonder",
                Role.ROLE_ACCOUNT_HOLDER
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.username").value("alice_test"))
                .andExpect(jsonPath("$.data.role").value("ROLE_ACCOUNT_HOLDER"));
    }

    @Test
    @DisplayName("3. Login with valid seed credentials should return token")
    void loginShouldSucceed() throws Exception {
        LoginRequest request = new LoginRequest("admin", "Admin@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("4. Login with wrong password should return 401 Unauthorized")
    void loginWithWrongPasswordShouldFail() throws Exception {
        LoginRequest request = new LoginRequest("admin", "WrongPassword999");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("5. Protected /api/auth/me without token should return 401")
    void getMeWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6. Protected /api/auth/me with valid Bearer token should return profile")
    void getMeWithTokenShouldSucceed() throws Exception {
        // Step 1: Login as john_doe
        LoginRequest loginRequest = new LoginRequest("john_doe", "User@123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("token").asText();

        // Step 2: Call /api/auth/me with token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("john_doe"))
                .andExpect(jsonPath("$.data.email").value("john@example.com"));
    }

    @Test
    @DisplayName("7. Non-admin accessing admin endpoint should return 403 Forbidden")
    void nonAdminAccessingAdminEndpointShouldReturn403() throws Exception {
        // Login as john_doe (ACCOUNT_HOLDER)
        LoginRequest loginRequest = new LoginRequest("john_doe", "User@123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("token").asText();

        // Try to access /api/auth/admin/users
        mockMvc.perform(get("/api/auth/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. Admin accessing admin endpoint should return 200 OK with users list")
    void adminAccessingAdminEndpointShouldSucceed() throws Exception {
        // Login as admin
        LoginRequest loginRequest = new LoginRequest("admin", "Admin@123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("data").get("token").asText();

        // Access /api/auth/admin/users
        mockMvc.perform(get("/api/auth/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
