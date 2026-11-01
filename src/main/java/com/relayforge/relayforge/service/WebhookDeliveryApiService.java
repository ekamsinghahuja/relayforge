package com.relayforge.relayforge.service;

import com.relayforge.relayforge.common.DeliveryNotFoundException;
import com.relayforge.relayforge.entity.WebhookDelivery;
import com.relayforge.relayforge.repository.WebhookDeliveryAttemptRepository;
import com.relayforge.relayforge.repository.WebhookDeliveryRepository;
import com.relayforge.relayforge.repository.WebhookEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class WebhookDeliveryApiService {
    private final WebhookDeliveryRepository deliveries;
    private final WebhookDeliveryAttemptRepository attempts;
    private final WebhookEventRepository events;
    private final DeliveryJobService jobs;

    public WebhookDeliveryApiService(WebhookDeliveryRepository deliveries, WebhookDeliveryAttemptRepository attempts,
            WebhookEventRepository events, DeliveryJobService jobs) {
        this.deliveries = deliveries; this.attempts = attempts; this.events = events; this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public Page<DeliveryView> list(String status, UUID eventId, UUID endpointId, int page, int size) {
        return deliveries.search(status, eventId, endpointId, PageRequest.of(Math.max(0,page), Math.min(100,Math.max(1,size)), Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by("id"))))
                .map(this::view);
    }
    @Transactional(readOnly = true)
    public DeliveryView get(UUID id) { return view(find(id)); }
    @Transactional(readOnly = true)
    public java.util.List<AttemptView> attempts(UUID id) {
        find(id);
        return attempts.findAllByDeliveryIdOrderByAttemptNumberAsc(id).stream().map(a -> new AttemptView(a.getId(),a.getAttemptNumber(),a.getStartedAt(),a.getFinishedAt(),a.getResponseStatus(),a.getOutcome(),a.getErrorDescription())).toList();
    }
    public void retry(UUID id) {
        WebhookDelivery d=find(id);
        if (!"FAILED".equals(d.getStatus()) || !jobs.retryManually(id)) throw new IllegalStateException("Only failed deliveries can be manually retried");
    }
    private WebhookDelivery find(UUID id) { return deliveries.findById(id).orElseThrow(() -> new DeliveryNotFoundException("Delivery not found: " + id)); }
    private DeliveryView view(WebhookDelivery d) {
        String type=events.findById(d.getEventId()).map(e -> e.getEventType()).orElse(null);
        return new DeliveryView(d.getId(),d.getEventId(),d.getEndpointId(),type,d.getStatus(),d.getAttemptCount(),d.getRetryIntervalSeconds(),d.getLastAttemptAt(),d.getLastResponseStatus(),d.getLastError(),d.getNextAttemptAt(),d.getCreatedAt(),d.getUpdatedAt());
    }
    public record DeliveryView(UUID id, UUID eventId, UUID endpointId, String eventType, String status, int attemptCount, int retryIntervalSeconds, OffsetDateTime lastAttemptAt, Integer lastResponseStatus, String lastError, OffsetDateTime nextAttemptAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record AttemptView(UUID id,int attemptNumber,OffsetDateTime startedAt,OffsetDateTime finishedAt,Integer responseStatus,String outcome,String errorDescription) {}
}
