package com.snovasys.ledgerline.controller;

import com.snovasys.ledgerline.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> checkHealth() {
        boolean dbHealthy = false;
        try (Connection connection = dataSource.getConnection()) {
            dbHealthy = connection.isValid(2);
        } catch (Exception e) {
            dbHealthy = false;
        }

        String status = dbHealthy ? "UP" : "DEGRADED";
        HealthResponse response = new HealthResponse(status, "LedgerLine-Backend", "1.0.0", dbHealthy);
        return ResponseEntity.ok(response);
    }
}

