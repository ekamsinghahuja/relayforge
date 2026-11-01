CREATE TABLE webhook_endpoint_subscriptions (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_webhook_endpoint_subscriptions_endpoint_event_type
        UNIQUE (endpoint_id, event_type)
);

CREATE INDEX idx_webhook_endpoint_subscriptions_event_type_endpoint_id
    ON webhook_endpoint_subscriptions(event_type, endpoint_id);
