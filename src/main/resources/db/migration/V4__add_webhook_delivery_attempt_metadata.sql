ALTER TABLE webhook_deliveries
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_response_status INTEGER,
    ADD COLUMN last_error VARCHAR(1000),
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN claim_token UUID;
