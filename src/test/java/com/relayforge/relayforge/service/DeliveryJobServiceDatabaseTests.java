package com.relayforge.relayforge.service;

import com.relayforge.relayforge.entity.WebhookDelivery;
import com.relayforge.relayforge.entity.WebhookEndpoint;
import com.relayforge.relayforge.entity.WebhookEvent;
import com.relayforge.relayforge.repository.WebhookDeliveryRepository;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import com.relayforge.relayforge.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "relayforge.delivery.enabled=false",
        "relayforge.delivery.batch-size=100000"
})
class DeliveryJobServiceDatabaseTests {

    @Autowired
    private DeliveryJobService jobService;

    @Autowired
    private WebhookEventRepository eventRepository;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional
    void recordsAttemptsAndReclaimsExpiredProcessingJobsWithANewToken() {
        WebhookDelivery delivery = createDelivery();

        List<DeliveryJobService.ClaimedDelivery> firstClaims = jobService.claimReadyDeliveries();
        DeliveryJobService.ClaimedDelivery firstClaim = claimFor(firstClaims, delivery.getId());
        assertNotNull(firstClaim);

        jdbcTemplate.update("""
                UPDATE webhook_deliveries
                SET processing_started_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes'
                WHERE id = ?
                """, delivery.getId());

        List<DeliveryJobService.ClaimedDelivery> recoveredClaims = jobService.claimReadyDeliveries();
        DeliveryJobService.ClaimedDelivery recoveredClaim = claimFor(recoveredClaims, delivery.getId());
        assertNotNull(recoveredClaim);
        assertNotEquals(firstClaim.claimToken(), recoveredClaim.claimToken());
        assertFalse(jobService.complete(firstClaim, "DELIVERED", 200, null));
        assertTrue(jobService.recordAttemptStarted(recoveredClaim));
        assertTrue(jobService.complete(recoveredClaim, "DELIVERED", 202, null));

        var row = jdbcTemplate.queryForMap("""
                SELECT status, attempt_count, last_attempt_at, last_response_status,
                       last_error, processing_started_at, claim_token
                FROM webhook_deliveries WHERE id = ?
                """, delivery.getId());
        assertEquals("DELIVERED", row.get("status"));
        assertEquals(1, row.get("attempt_count"));
        assertNotNull(row.get("last_attempt_at"));
        assertEquals(202, row.get("last_response_status"));
        assertEquals(null, row.get("last_error"));
        assertEquals(null, row.get("processing_started_at"));
        assertEquals(null, row.get("claim_token"));
    }

    @Test
    @Transactional
    void schedulesRetryUsingTheDeliverySpecificFixedInterval() {
        WebhookDelivery delivery = createDelivery();
        delivery.setRetryIntervalSeconds(37);
        deliveryRepository.saveAndFlush(delivery);
        DeliveryJobService.ClaimedDelivery claim = claimFor(
                jobService.claimReadyDeliveries(), delivery.getId());
        assertNotNull(claim);
        assertTrue(jobService.recordAttemptStarted(claim));

        assertTrue(jobService.scheduleRetry(claim, 503, "Receiver returned HTTP 503",
                delivery.getRetryIntervalSeconds()));

        var row = jdbcTemplate.queryForMap("""
                SELECT status, attempt_count,
                       EXTRACT(EPOCH FROM (next_attempt_at - CURRENT_TIMESTAMP))::integer AS seconds_left
                FROM webhook_deliveries WHERE id = ?
                """, delivery.getId());
        assertEquals("RETRY_WAIT", row.get("status"));
        assertEquals(1, row.get("attempt_count"));
        int secondsLeft = ((Number) row.get("seconds_left")).intValue();
        assertTrue(secondsLeft >= 35 && secondsLeft <= 37, "expected the configured 37 second delay");
    }

    @Test
    void concurrentClaimTransactionsDoNotClaimTheSameDelivery() throws Exception {
        WebhookDelivery delivery = createDelivery();
        CountDownLatch firstClaimIsLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstClaim = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            var firstWorker = executor.submit(() -> transactionTemplate.execute(status -> {
                List<DeliveryJobService.ClaimedDelivery> claims = jobService.claimReadyDeliveries();
                firstClaimIsLocked.countDown();
                await(releaseFirstClaim);
                status.setRollbackOnly();
                return claims;
            }));

            assertTrue(firstClaimIsLocked.await(5, TimeUnit.SECONDS));
            List<DeliveryJobService.ClaimedDelivery> secondClaims =
                    jobService.claimReadyDeliveries();
            assertTrue(secondClaims.stream().noneMatch(claim -> claim.id().equals(delivery.getId())));

            releaseFirstClaim.countDown();
            List<DeliveryJobService.ClaimedDelivery> firstClaims = firstWorker.get(5, TimeUnit.SECONDS);
            assertTrue(firstClaims.stream().anyMatch(claim -> claim.id().equals(delivery.getId())));
        } finally {
            releaseFirstClaim.countDown();
            executor.shutdownNow();
            deliveryRepository.deleteById(delivery.getId());
            eventRepository.deleteById(delivery.getEventId());
            endpointRepository.deleteById(delivery.getEndpointId());
        }
    }

    private WebhookDelivery createDelivery() {
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        WebhookEvent event = new WebhookEvent();
        event.setId(eventId);
        event.setEventType("worker.test");
        event.setPayload("{}");
        event.setStatus("PENDING");
        event.setCreatedAt(OffsetDateTime.now());
        eventRepository.saveAndFlush(event);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(endpointId);
        endpoint.setUrl("https://example.test/hook");
        endpoint.setActive(true);
        endpoint.setCreatedAt(OffsetDateTime.now());
        endpointRepository.saveAndFlush(endpoint);

        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(deliveryId);
        delivery.setEventId(eventId);
        delivery.setEndpointId(endpointId);
        delivery.setStatus("PENDING");
        delivery.setAttemptCount(0);
        delivery.setCreatedAt(OffsetDateTime.parse("2000-01-01T00:00:00Z"));
        return deliveryRepository.saveAndFlush(delivery);
    }

    private DeliveryJobService.ClaimedDelivery claimFor(
            List<DeliveryJobService.ClaimedDelivery> claims, UUID id) {
        return claims.stream().filter(claim -> claim.id().equals(id)).findFirst().orElse(null);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release test claim");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding test claim", exception);
        }
    }
}
