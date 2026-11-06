package com.snovasys.ledgerline.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final String jwtSecret;
    private final long jwtExpirationMs;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(
            @Value("${ledgerline.jwt.secret:9a4f2c8d3e7b1a5f6c8d0e2b4a6c8e1f3a5b7d9e1f3a5b7c9d1e3f5a7b9c1d3e}") String jwtSecret,
            @Value("${ledgerline.jwt.expiration-ms:86400000}") long jwtExpirationMs,
            ObjectMapper objectMapper) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
        this.objectMapper = objectMapper;
    }

    public String generateToken(String username, String role) {
        try {
            long now = System.currentTimeMillis();
            long expiry = now + jwtExpirationMs;

            // 1. Header
            Map<String, Object> headerMap = new HashMap<>();
            headerMap.put("alg", "HS256");
            headerMap.put("typ", "JWT");
            String headerJson = objectMapper.writeValueAsString(headerMap);
            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

            // 2. Payload
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("sub", username);
            payloadMap.put("role", role);
            payloadMap.put("iat", now / 1000);
            payloadMap.put("exp", expiry / 1000);
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            // 3. Signature
            String contentToSign = encodedHeader + "." + encodedPayload;
            String signature = sign(contentToSign);

            return contentToSign + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            String contentToSign = parts[0] + "." + parts[1];
            String expectedSignature = sign(contentToSign);
            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            // Check expiration
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payloadNode = objectMapper.readTree(payloadJson);
            if (!payloadNode.has("exp")) {
                return false;
            }

            long expSeconds = payloadNode.get("exp").asLong();
            long nowSeconds = System.currentTimeMillis() / 1000;
            return nowSeconds < expSeconds;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payloadNode = objectMapper.readTree(payloadJson);
            return payloadNode.get("sub").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract username from token", e);
        }
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    private String sign(String data) throws Exception {
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSha256.init(secretKey);
        byte[] hash = hmacSha256.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
