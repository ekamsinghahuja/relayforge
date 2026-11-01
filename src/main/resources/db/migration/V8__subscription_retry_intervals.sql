ALTER TABLE webhook_endpoint_subscriptions
    ADD COLUMN retry_interval_seconds INTEGER NOT NULL DEFAULT 60,
    ADD CONSTRAINT ck_webhook_subscription_retry_interval
        CHECK (retry_interval_seconds BETWEEN 1 AND 604800);

ALTER TABLE webhook_deliveries
    ADD COLUMN retry_interval_seconds INTEGER NOT NULL DEFAULT 60,
    ADD CONSTRAINT ck_webhook_delivery_retry_interval
        CHECK (retry_interval_seconds BETWEEN 1 AND 604800);
