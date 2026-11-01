package com.relayforge.relayforge.repository;

import com.relayforge.relayforge.entity.WebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSubscriptionRepository
        extends JpaRepository<WebhookSubscription, UUID> {

    List<WebhookSubscription> findAllByEndpointIdOrderByCreatedAtAsc(UUID endpointId);

    List<WebhookSubscription> findAllByEventType(String eventType);

    Optional<WebhookSubscription> findByEndpointIdAndEventType(UUID endpointId, String eventType);

    boolean existsByEndpointIdAndEventType(UUID endpointId, String eventType);
}
