ALTER TABLE webhook_events ADD COLUMN idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX uq_webhook_events_idempotency_key
    ON webhook_events(idempotency_key) WHERE idempotency_key IS NOT NULL;
