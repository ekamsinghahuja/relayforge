UPDATE webhook_events AS e
SET status = 'DONE'
WHERE NOT EXISTS (
    SELECT 1
    FROM webhook_deliveries AS d
    WHERE d.event_id = e.id
      AND d.status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT')
);
