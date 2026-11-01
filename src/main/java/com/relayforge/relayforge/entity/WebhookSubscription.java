package com.relayforge.relayforge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoint_subscriptions")
public class WebhookSubscription {

    @Id
    private UUID id;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "retry_interval_seconds", nullable = false)
    private int retryIntervalSeconds = 60;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public WebhookSubscription() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public int getRetryIntervalSeconds() { return retryIntervalSeconds; }
    public void setRetryIntervalSeconds(int retryIntervalSeconds) { this.retryIntervalSeconds = retryIntervalSeconds; }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
