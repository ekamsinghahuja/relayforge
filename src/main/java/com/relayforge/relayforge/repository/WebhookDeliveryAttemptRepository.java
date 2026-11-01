package com.relayforge.relayforge.repository;

import com.relayforge.relayforge.entity.WebhookDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryAttemptRepository extends JpaRepository<WebhookDeliveryAttempt, UUID> {
    List<WebhookDeliveryAttempt> findAllByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);
}
