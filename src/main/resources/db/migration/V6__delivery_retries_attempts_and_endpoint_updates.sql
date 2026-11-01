ALTER TABLE webhook_endpoints
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN signing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN signing_secret_encrypted TEXT;


ALTER TABLE webhook_deliveries
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_webhook_deliveries_status_next_attempt
    ON webhook_deliveries(status, next_attempt_at, created_at);

CREATE TABLE webhook_delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES webhook_deliveries(id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    response_status INTEGER,
    outcome VARCHAR(30),
    error_description VARCHAR(1000),
    response_body VARCHAR(4096),
    CONSTRAINT uq_webhook_delivery_attempt_number UNIQUE(delivery_id, attempt_number)
);

CREATE INDEX idx_webhook_delivery_attempts_delivery_id
    ON webhook_delivery_attempts(delivery_id, attempt_number);
