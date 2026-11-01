
package com.relayforge.relayforge.service;

import com.relayforge.relayforge.entity.WebhookEvent;
import com.relayforge.relayforge.entity.WebhookDelivery;
import com.relayforge.relayforge.entity.WebhookEndpoint;
import com.relayforge.relayforge.common.EventNotFoundException;
import com.relayforge.relayforge.repository.WebhookDeliveryRepository;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import com.relayforge.relayforge.repository.WebhookEventRepository;
import com.relayforge.relayforge.repository.WebhookSubscriptionRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.relayforge.relayforge.common.DuplicateEventKeyException;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class WebhookEventService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final WebhookEventRepository repository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;

    public WebhookEventService(
            WebhookEventRepository repository,
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookSubscriptionRepository subscriptionRepository) {
        this.repository = repository;
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public EventResponse createEvent(String eventType, JsonNode payload, String idempotencyKey) {
        String payloadString = payload.toString();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                WebhookEvent event = existing.get();
                if (!eventType.equals(event.getEventType()) || !samePayload(payload, event.getPayload()))
                    throw new DuplicateEventKeyException("Idempotency-Key was already used with a different request");
                return toResponse(event);
            }
        }
        WebhookEvent event = new WebhookEvent();

        event.setId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setPayload(payloadString);
        event.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey);
        event.setStatus("PENDING");
        event.setCreatedAt(OffsetDateTime.now());

        WebhookEvent savedEvent;
        try { savedEvent = repository.saveAndFlush(event); }
        catch (DataIntegrityViolationException ex) {
            if (idempotencyKey == null) throw ex;
            WebhookEvent prior = repository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex);
            if (!eventType.equals(prior.getEventType()) || !samePayload(payload, prior.getPayload()))
                throw new DuplicateEventKeyException("Idempotency-Key was already used with a different request");
            return toResponse(prior);
        }
        var retryIntervals = subscriptionRepository.findAllByEventType(savedEvent.getEventType()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.relayforge.relayforge.entity.WebhookSubscription::getEndpointId,
                        com.relayforge.relayforge.entity.WebhookSubscription::getRetryIntervalSeconds));
        List<WebhookDelivery> deliveries = endpointRepository
                .findActiveSubscribedToEventType(savedEvent.getEventType())
                .stream()
                .map(endpoint -> createDelivery(savedEvent, endpoint,
                        retryIntervals.getOrDefault(endpoint.getId(), 60)))
                .toList();
        deliveryRepository.saveAll(deliveries);
        if (deliveries.isEmpty()) {
            savedEvent.setStatus("DONE");
        }

        return toResponse(savedEvent);
    }

    @Transactional
    public WebhookEvent createEvent(String eventType, JsonNode payload) {
        EventResponse response = createEvent(eventType, payload, null);
        WebhookEvent event = new WebhookEvent();
        event.setId(response.id()); event.setEventType(response.eventType());
        event.setPayload(response.payload()); event.setStatus(response.status()); event.setCreatedAt(response.createdAt());
        return event;
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listEvents(String eventType, int page, int size) {
        return repository.search(eventType, PageRequest.of(Math.max(0,page), Math.min(100,Math.max(1,size)), Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by("id"))))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(UUID id) {
        return repository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + id));
    }

    private EventResponse toResponse(WebhookEvent event) {
        return new EventResponse(event.getId(), event.getEventType(), event.getPayload(), event.getStatus(), event.getCreatedAt());
    }

    private boolean samePayload(JsonNode requestedPayload, String storedPayload) {
        try {
            return JSON_MAPPER.readTree(storedPayload).equals(requestedPayload);
        } catch (Exception exception) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getDeliveries(UUID eventId) {
        if (!repository.existsById(eventId)) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        return deliveryRepository.findAllByEventId(eventId).stream()
                .map(delivery -> new DeliveryResponse(
                        delivery.getId(),
                        delivery.getEndpointId(),
                        delivery.getStatus(),
                        delivery.getAttemptCount(),
                        delivery.getRetryIntervalSeconds(),
                        delivery.getCreatedAt()))
                .toList();
    }

    private WebhookDelivery createDelivery(
            WebhookEvent event, WebhookEndpoint endpoint, int retryIntervalSeconds) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(UUID.randomUUID());
        delivery.setEventId(event.getId());
        delivery.setEndpointId(endpoint.getId());
        delivery.setStatus("PENDING");
        delivery.setAttemptCount(0);
        delivery.setRetryIntervalSeconds(retryIntervalSeconds);
        delivery.setCreatedAt(OffsetDateTime.now());
        return delivery;
    }

    public record DeliveryResponse(
            UUID id,
            UUID endpointId,
            String status,
            int attemptCount,
            int retryIntervalSeconds,
            OffsetDateTime createdAt) {
    }

    public record EventResponse(UUID id, String eventType, String payload, String status, OffsetDateTime createdAt) {}
}
