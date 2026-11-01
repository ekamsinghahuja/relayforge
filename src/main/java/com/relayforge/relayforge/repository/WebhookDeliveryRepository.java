package com.relayforge.relayforge.repository;

import com.relayforge.relayforge.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository
        extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findAllByEventId(UUID eventId);

    @Query("""
            select d from WebhookDelivery d
            where (:status is null or d.status = :status)
              and (:eventId is null or d.eventId = :eventId)
              and (:endpointId is null or d.endpointId = :endpointId)
            """)
    Page<WebhookDelivery> search(@Param("status") String status,
            @Param("eventId") UUID eventId, @Param("endpointId") UUID endpointId,
            Pageable pageable);
}
