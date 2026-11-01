ALTER TABLE webhook_endpoints
    ADD COLUMN name VARCHAR(255);

UPDATE webhook_endpoints
SET name = COALESCE(NULLIF(description, ''), LEFT(url, 255));

ALTER TABLE webhook_endpoints
    ALTER COLUMN name SET NOT NULL;
