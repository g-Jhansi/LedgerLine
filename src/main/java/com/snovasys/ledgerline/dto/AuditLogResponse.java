package com.snovasys.ledgerline.dto;

import com.snovasys.ledgerline.entity.AuditAction;
import com.snovasys.ledgerline.entity.AuditLog;
import com.snovasys.ledgerline.entity.AuditStatus;

import java.time.LocalDateTime;

public class AuditLogResponse {

    private Long id;
    private AuditAction action;
    private String actorUsername;
    private String actorRole;
    private String ipAddress;
    private String userAgent;
    private String entityType;
    private String entityId;
    private String details;
    private AuditStatus status;
    private String previousHash;
    private String currentHash;
    private LocalDateTime createdAt;

    public AuditLogResponse() {
    }

    public static AuditLogResponse fromEntity(AuditLog log) {
        AuditLogResponse res = new AuditLogResponse();
        res.setId(log.getId());
        res.setAction(log.getAction());
        res.setActorUsername(log.getActorUsername());
        res.setActorRole(log.getActorRole());
        res.setIpAddress(log.getIpAddress());
        res.setUserAgent(log.getUserAgent());
        res.setEntityType(log.getEntityType());
        res.setEntityId(log.getEntityId());
        res.setDetails(log.getDetails());
        res.setStatus(log.getStatus());
        res.setPreviousHash(log.getPreviousHash());
        res.setCurrentHash(log.getCurrentHash());
        res.setCreatedAt(log.getCreatedAt());
        return res;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public void setStatus(AuditStatus status) {
        this.status = status;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setCurrentHash(String currentHash) {
        this.currentHash = currentHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

