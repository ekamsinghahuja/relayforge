package com.relayforge.relayforge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    private UUID id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "signing_enabled", nullable = false)
    private boolean signingEnabled;

    @Column(name = "signing_secret_encrypted")
    private String signingSecretEncrypted;

    public WebhookEndpoint() {
    }

    @jakarta.persistence.PrePersist
    void initializeTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isSigningEnabled() { return signingEnabled; }
    public void setSigningEnabled(boolean signingEnabled) { this.signingEnabled = signingEnabled; }
    public String getSigningSecretEncrypted() { return signingSecretEncrypted; }
    public void setSigningSecretEncrypted(String signingSecretEncrypted) { this.signingSecretEncrypted = signingSecretEncrypted; }
}
