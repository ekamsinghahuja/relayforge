
package com.relayforge.relayforge.repository;

import com.relayforge.relayforge.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository
        extends JpaRepository<WebhookEndpoint, UUID> {
        
            Optional<WebhookEndpoint> findById(UUID id);

    List<WebhookEndpoint> findAllByActiveTrue();

    @Query(value = """
            SELECT endpoint.*
            FROM webhook_endpoints endpoint
            JOIN webhook_endpoint_subscriptions subscription
              ON subscription.endpoint_id = endpoint.id
            WHERE endpoint.active = TRUE
              AND subscription.event_type = :eventType
            ORDER BY endpoint.created_at, endpoint.id
            """, nativeQuery = true)
    List<WebhookEndpoint> findActiveSubscribedToEventType(
            @Param("eventType") String eventType);
}
