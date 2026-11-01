package com.relayforge.relayforge.service;

import com.relayforge.relayforge.common.EventNotFoundException;
import com.relayforge.relayforge.entity.WebhookDelivery;
import com.relayforge.relayforge.entity.WebhookEndpoint;
import com.relayforge.relayforge.entity.WebhookEvent;
import com.relayforge.relayforge.entity.WebhookSubscription;
import com.relayforge.relayforge.repository.WebhookDeliveryRepository;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import com.relayforge.relayforge.repository.WebhookEventRepository;
import com.relayforge.relayforge.repository.WebhookSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookEventServiceTests {

    private WebhookEventRepository eventRepository;
    private WebhookEndpointRepository endpointRepository;
    private WebhookDeliveryRepository deliveryRepository;
    private WebhookSubscriptionRepository subscriptionRepository;
    private WebhookEventService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(WebhookEventRepository.class);
        endpointRepository = mock(WebhookEndpointRepository.class);
        deliveryRepository = mock(WebhookDeliveryRepository.class);
        subscriptionRepository = mock(WebhookSubscriptionRepository.class);
        service = new WebhookEventService(
                eventRepository, endpointRepository, deliveryRepository, subscriptionRepository);
        when(eventRepository.saveAndFlush(any(WebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryRepository.saveAll(any(Iterable.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsOnePendingDeliveryForEachActiveEndpoint() throws Exception {
        WebhookEndpoint first = endpoint(UUID.randomUUID(), true);
        WebhookEndpoint second = endpoint(UUID.randomUUID(), true);
        when(endpointRepository.findActiveSubscribedToEventType("order.created"))
                .thenReturn(List.of(first, second));
        WebhookSubscription firstSubscription = subscription(first.getId(), 45);
        WebhookSubscription secondSubscription = subscription(second.getId(), 90);
        when(subscriptionRepository.findAllByEventType("order.created"))
                .thenReturn(List.of(firstSubscription, secondSubscription));

        WebhookEvent event = service.createEvent(
                "order.created", JsonMapper.builder().build().readTree("{\"id\":1}"));

        verify(eventRepository).saveAndFlush(any(WebhookEvent.class));
        var deliveries = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(deliveryRepository).saveAll(deliveries.capture());
        List<WebhookDelivery> saved = toList(deliveries.getValue());
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(delivery ->
                delivery.getEventId().equals(event.getId())
                        && "PENDING".equals(delivery.getStatus())
                        && delivery.getAttemptCount() == 0));
        assertEquals(List.of(first.getId(), second.getId()),
                saved.stream().map(WebhookDelivery::getEndpointId).toList());
        assertEquals(List.of(45, 90), saved.stream().map(WebhookDelivery::getRetryIntervalSeconds).toList());
    }

    @Test
    void skipsInactiveEndpoints() throws Exception {
        WebhookEndpoint active = endpoint(UUID.randomUUID(), true);
        when(endpointRepository.findActiveSubscribedToEventType("order.created"))
                .thenReturn(List.of(active));

        service.createEvent("order.created", JsonMapper.builder().build().readTree("{}"));

        var deliveries = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(deliveryRepository).saveAll(deliveries.capture());
        List<WebhookDelivery> saved = toList(deliveries.getValue());
        assertEquals(1, saved.size());
        assertEquals(active.getId(), saved.getFirst().getEndpointId());
    }

    @Test
    void savesEventWhenThereAreNoActiveEndpoints() throws Exception {
        when(endpointRepository.findActiveSubscribedToEventType("order.created"))
                .thenReturn(List.of());

        WebhookEvent event = service.createEvent(
                "order.created", JsonMapper.builder().build().readTree("{}"));

        assertEquals("PENDING", event.getStatus());
        verify(eventRepository).saveAndFlush(any(WebhookEvent.class));
        verify(deliveryRepository).saveAll(List.of());
    }

    @Test
    void returnsDeliverySummariesForAnExistingEvent() {
        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(deliveryId);
        delivery.setEventId(eventId);
        delivery.setEndpointId(endpointId);
        delivery.setStatus("PENDING");
        delivery.setAttemptCount(0);
        delivery.setCreatedAt(createdAt);
        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(deliveryRepository.findAllByEventId(eventId)).thenReturn(List.of(delivery));

        List<WebhookEventService.DeliveryResponse> result = service.getDeliveries(eventId);

        assertEquals(List.of(new WebhookEventService.DeliveryResponse(
                deliveryId, endpointId, "PENDING", 0, 60, createdAt)), result);
    }

    @Test
    void missingEventRaisesNotFound() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.existsById(eventId)).thenReturn(false);

        assertThrows(EventNotFoundException.class, () -> service.getDeliveries(eventId));
    }

    @Test
    void migrationAndEntityUseTheDeliveryTableAndColumns() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V3__create_webhook_deliveries.sql"));

        assertEquals("webhook_deliveries", WebhookDelivery.class
                .getAnnotation(jakarta.persistence.Table.class).name());
        assertTrue(migration.contains("event_id UUID NOT NULL REFERENCES webhook_events(id)"));
        assertTrue(migration.contains("endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id)"));
        assertTrue(migration.contains("UNIQUE (event_id, endpoint_id)"));
        assertTrue(migration.contains("ON webhook_deliveries(event_id)"));
    }

    @Test
    void subscriptionEntityAndMigrationDefineUniqueEndpointEventPairs() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V5__create_webhook_endpoint_subscriptions.sql"));

        assertEquals("webhook_endpoint_subscriptions", WebhookSubscription.class
                .getAnnotation(jakarta.persistence.Table.class).name());
        assertTrue(migration.contains("REFERENCES webhook_endpoints(id) ON DELETE CASCADE"));
        assertTrue(migration.contains("UNIQUE (endpoint_id, event_type)"));
        assertTrue(migration.contains("ON webhook_endpoint_subscriptions(event_type, endpoint_id)"));
    }

    @Test
    void retryIntervalMigrationAddsDefaultAndBoundsForSubscriptionsAndDeliveries() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V8__subscription_retry_intervals.sql"));
        assertTrue(migration.contains("retry_interval_seconds INTEGER NOT NULL DEFAULT 60"));
        assertTrue(migration.contains("CHECK (retry_interval_seconds BETWEEN 1 AND 604800)"));
        assertTrue(migration.contains("ALTER TABLE webhook_endpoint_subscriptions"));
        assertTrue(migration.contains("ALTER TABLE webhook_deliveries"));
        assertTrue(java.util.Arrays.stream(WebhookSubscription.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("retryIntervalSeconds")));
        assertTrue(java.util.Arrays.stream(WebhookDelivery.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("retryIntervalSeconds")));
    }

    private WebhookEndpoint endpoint(UUID id, boolean active) {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(id);
        endpoint.setActive(active);
        return endpoint;
    }

    private WebhookSubscription subscription(UUID endpointId, int retryIntervalSeconds) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setEndpointId(endpointId);
        subscription.setRetryIntervalSeconds(retryIntervalSeconds);
        return subscription;
    }

    private List<WebhookDelivery> toList(Iterable<WebhookDelivery> deliveries) {
        java.util.ArrayList<WebhookDelivery> result = new java.util.ArrayList<>();
        deliveries.forEach(result::add);
        return result;
    }
}
