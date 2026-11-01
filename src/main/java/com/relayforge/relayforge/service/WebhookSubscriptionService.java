package com.relayforge.relayforge.service;

import com.relayforge.relayforge.common.DuplicateSubscriptionException;
import com.relayforge.relayforge.common.EndpointNotFoundException;
import com.relayforge.relayforge.common.SubscriptionNotFoundException;
import com.relayforge.relayforge.entity.WebhookSubscription;
import com.relayforge.relayforge.repository.WebhookEndpointRepository;
import com.relayforge.relayforge.repository.WebhookSubscriptionRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookSubscriptionService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;

    public WebhookSubscriptionService(
            WebhookEndpointRepository endpointRepository,
            WebhookSubscriptionRepository subscriptionRepository) {
        this.endpointRepository = endpointRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public SubscriptionResponse createSubscription(UUID endpointId, String eventType, Integer retryIntervalSeconds) {
        ensureEndpointExists(endpointId);
        if (subscriptionRepository.existsByEndpointIdAndEventType(endpointId, eventType)) {
            throw duplicate(endpointId, eventType);
        }

        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setEndpointId(endpointId);
        subscription.setEventType(eventType);
        subscription.setRetryIntervalSeconds(retryIntervalSeconds == null ? 60 : retryIntervalSeconds);
        subscription.setCreatedAt(OffsetDateTime.now());
        try {
            return toResponse(subscriptionRepository.saveAndFlush(subscription));
        } catch (DataIntegrityViolationException exception) {
            // The database unique constraint also closes the concurrent-create race.
            if (violatesSubscriptionUniqueConstraint(exception)) {
                throw duplicate(endpointId, eventType);
            }
            throw exception;
        }
    }

    @Transactional
    public SubscriptionResponse updateRetryInterval(UUID endpointId, String eventType, int retryIntervalSeconds) {
        ensureEndpointExists(endpointId);
        WebhookSubscription subscription = subscriptionRepository.findByEndpointIdAndEventType(endpointId, eventType)
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "Subscription not found for endpoint " + endpointId + " and event type " + eventType));
        subscription.setRetryIntervalSeconds(retryIntervalSeconds);
        return toResponse(subscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getSubscriptions(UUID endpointId) {
        ensureEndpointExists(endpointId);
        return subscriptionRepository.findAllByEndpointIdOrderByCreatedAtAsc(endpointId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteSubscription(UUID endpointId, String eventType) {
        ensureEndpointExists(endpointId);
        WebhookSubscription subscription = subscriptionRepository
                .findByEndpointIdAndEventType(endpointId, eventType)
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "Subscription not found for endpoint " + endpointId
                                + " and event type " + eventType));
        subscriptionRepository.delete(subscription);
    }

    private void ensureEndpointExists(UUID endpointId) {
        if (!endpointRepository.existsById(endpointId)) {
            throw new EndpointNotFoundException("Endpoint not found: " + endpointId);
        }
    }

    private DuplicateSubscriptionException duplicate(UUID endpointId, String eventType) {
        return new DuplicateSubscriptionException(
                "Endpoint " + endpointId + " is already subscribed to " + eventType);
    }

    private boolean violatesSubscriptionUniqueConstraint(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "uq_webhook_endpoint_subscriptions_endpoint_event_type"
                            .equals(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private SubscriptionResponse toResponse(WebhookSubscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getEndpointId(),
                subscription.getEventType(),
                subscription.getRetryIntervalSeconds(),
                subscription.getCreatedAt());
    }

    public record SubscriptionResponse(
            UUID id,
            UUID endpointId,
            String eventType,
            int retryIntervalSeconds,
            OffsetDateTime createdAt) {
    }
}
