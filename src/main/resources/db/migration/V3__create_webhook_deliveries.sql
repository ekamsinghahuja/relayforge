CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES webhook_events(id),
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_webhook_deliveries_event_endpoint UNIQUE (event_id, endpoint_id)
);

CREATE INDEX idx_webhook_deliveries_event_id
    ON webhook_deliveries(event_id);
