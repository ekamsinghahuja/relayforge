
package com.relayforge.relayforge.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.relayforge.relayforge.entity.WebhookEvent;
import com.relayforge.relayforge.service.WebhookEventService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class WebhookEventController {

    private final WebhookEventService service;

    public WebhookEventController(WebhookEventService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookEventService.EventResponse createEvent(@Valid @RequestBody CreateEventRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.createEvent(
                request.eventType(),
                request.payload(), idempotencyKey);
    }

    @GetMapping
    public org.springframework.data.domain.Page<WebhookEventService.EventResponse> listEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listEvents(eventType, page, size);
    }

    @GetMapping("/{eventId}")
    public WebhookEventService.EventResponse getEvent(@PathVariable UUID eventId) { return service.getEvent(eventId); }

    @GetMapping("/{eventId}/deliveries")
    public List<WebhookEventService.DeliveryResponse> getDeliveries(
            @PathVariable UUID eventId) {
        return service.getDeliveries(eventId);
    }

    public record CreateEventRequest(
            @NotBlank String eventType,
            @NotNull JsonNode payload) {

    }
}
