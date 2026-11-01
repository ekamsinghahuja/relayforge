
package com.relayforge.relayforge.repository;

import com.relayforge.relayforge.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository
        extends JpaRepository<WebhookEvent, UUID> {

    java.util.Optional<WebhookEvent> findByIdempotencyKey(String idempotencyKey);

    @Query("select e from WebhookEvent e where (:eventType is null or e.eventType = :eventType)")
    Page<WebhookEvent> search(@Param("eventType") String eventType, Pageable pageable);
}
