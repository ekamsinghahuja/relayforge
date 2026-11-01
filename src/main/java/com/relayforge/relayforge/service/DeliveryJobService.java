package com.relayforge.relayforge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeliveryJobService {

    private static final String CLAIM_SQL = """
            WITH candidates AS (
                SELECT id
                FROM webhook_deliveries
                WHERE status = 'PENDING'
                   OR (status = 'RETRY_WAIT' AND next_attempt_at <= CURRENT_TIMESTAMP)
                   OR (status = 'PROCESSING'
                       AND processing_started_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 second'))
                ORDER BY created_at, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE webhook_deliveries AS delivery
            SET status = 'PROCESSING',
                processing_started_at = CURRENT_TIMESTAMP,
                next_attempt_at = NULL,
                claim_token = ?
            FROM candidates
            WHERE delivery.id = candidates.id
            RETURNING delivery.id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;
    private final int leaseDurationSeconds;
    private final int maxAttempts;

    public DeliveryJobService(
            JdbcTemplate jdbcTemplate,
            @Value("${relayforge.delivery.batch-size:25}") int batchSize,
            @Value("${relayforge.delivery.lease-duration-seconds:60}") int leaseDurationSeconds,
            @Value("${relayforge.delivery.max-attempts:5}") int maxAttempts) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize;
        this.leaseDurationSeconds = leaseDurationSeconds;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public List<ClaimedDelivery> claimReadyDeliveries() {
        UUID claimToken = UUID.randomUUID();
        return jdbcTemplate.query(
                CLAIM_SQL,
                (resultSet, rowNumber) -> new ClaimedDelivery(
                        resultSet.getObject("id", UUID.class), claimToken),
                leaseDurationSeconds,
                batchSize,
                claimToken);
    }

    @Transactional
    public boolean recordAttemptStarted(ClaimedDelivery claim) {
        boolean started = jdbcTemplate.update("""
                UPDATE webhook_deliveries
                SET attempt_count = attempt_count + 1,
                    last_attempt_at = CURRENT_TIMESTAMP,
                    last_error = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, claim.id(), claim.claimToken()) == 1;
        if (started) {
            Integer number = jdbcTemplate.queryForObject("select attempt_count from webhook_deliveries where id=?", Integer.class, claim.id());
            jdbcTemplate.update("INSERT INTO webhook_delivery_attempts(id, delivery_id, attempt_number) VALUES (?, ?, ?)", UUID.randomUUID(), claim.id(), number);
        }
        return started;
    }

    public int maxAttempts() { return maxAttempts; }

    @Transactional
    public boolean scheduleRetry(ClaimedDelivery claim, Integer responseStatus, String error,
            int retryIntervalSeconds) {
        String safeError = bounded(error);
        int attempt = jdbcTemplate.queryForObject("select attempt_count from webhook_deliveries where id = ?", Integer.class, claim.id());
        if (attempt >= maxAttempts) {
            return complete(claim, "FAILED", responseStatus, safeError);
        }
        long delay = Math.max(1, retryIntervalSeconds) * 1000L;
        boolean scheduled = jdbcTemplate.update("""
                UPDATE webhook_deliveries SET status='RETRY_WAIT', last_response_status=?, last_error=?,
                    next_attempt_at=CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                    processing_started_at=NULL, claim_token=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='PROCESSING' AND claim_token=?
                """, responseStatus, safeError, delay, claim.id(), claim.claimToken()) == 1;
        if (scheduled) finishAttempt(claim.id(), responseStatus, "RETRY_SCHEDULED", safeError);
        return scheduled;
    }

    @Transactional
    public boolean retryManually(UUID id) {
        return jdbcTemplate.update("""
                UPDATE webhook_deliveries SET status='PENDING', attempt_count=0,
                    next_attempt_at=NULL, last_error=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='FAILED'
                """, id) == 1;
    }

    private String bounded(String error) {
        return error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }

    private void finishAttempt(UUID deliveryId, Integer status, String outcome, String error) {
        jdbcTemplate.update("""
                UPDATE webhook_delivery_attempts SET finished_at=CURRENT_TIMESTAMP,
                    response_status=?, outcome=?, error_description=?
                WHERE id=(SELECT id FROM webhook_delivery_attempts WHERE delivery_id=? ORDER BY attempt_number DESC LIMIT 1)
                """, status, outcome, bounded(error), deliveryId);
    }

    @Transactional
    public boolean complete(
            ClaimedDelivery claim,
            String status,
            Integer responseStatus,
            String error) {
        String boundedError = error == null ? null
                : error.substring(0, Math.min(error.length(), 1000));
        boolean completed = jdbcTemplate.update("""
                UPDATE webhook_deliveries
                SET status = ?,
                    last_response_status = ?,
                    last_error = ?,
                    processing_started_at = NULL,
                    claim_token = NULL, next_attempt_at=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?
                """, status, responseStatus, boundedError,
                claim.id(), claim.claimToken()) == 1;
        if (completed && status != null && !"CANCELLED".equals(status))
            finishAttempt(claim.id(), responseStatus, status, boundedError);
        return completed;
    }

    public record ClaimedDelivery(UUID id, UUID claimToken) {
    }
}
